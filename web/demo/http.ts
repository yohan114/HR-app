/**
 * The plumbing between a Node request and a demo handler: routing, body reading, and the one
 * error envelope.
 *
 * Small on purpose. Everything here exists because a handler that has to parse its own URL and
 * assemble its own error body will eventually assemble a *slightly* different error body, and the
 * console reads `error.code` to decide what to tell the user. An envelope that is nearly right is
 * worse than one that is obviously wrong: the console falls through to "Something went wrong",
 * which is exactly the message you get when the whole thing is broken.
 */

import { Buffer } from 'node:buffer'
import type { IncomingMessage, ServerResponse } from 'node:http'
import type { ApiErrorResponse } from '@hr/client'

/* -------------------------------------------------------------------------- */
/* Requests                                                                    */
/* -------------------------------------------------------------------------- */

export interface DemoRequest {
  method: string
  path: string
  query: URLSearchParams
  params: Readonly<Record<string, string>>
  /** Parsed JSON body, or `undefined` for a request that carried none. */
  body: unknown
  header: (name: string) => string | undefined
  /** Correlates this request with the line the dev server logged for it. */
  requestId: string
}

export interface DemoReply {
  status: number
  /** Omitted for 204, which must not carry one. */
  body?: unknown
}

export type DemoHandler = (request: DemoRequest) => DemoReply | Promise<DemoReply>

/* -------------------------------------------------------------------------- */
/* Failures                                                                    */
/* -------------------------------------------------------------------------- */

/**
 * A failure shaped like `com.hr.shared.api.ApiException`.
 *
 * Thrown rather than returned so that a rule can refuse deep inside a handler without every caller
 * above it having to thread the refusal back out — which is how a check ends up being written and
 * then not acted on.
 */
export class ApiFailure extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly field?: string,
    readonly details?: Record<string, unknown>,
  ) {
    super(message)
    this.name = 'ApiFailure'
  }
}

export const badRequest = (code: string, message: string, field?: string, details?: Record<string, unknown>) =>
  new ApiFailure(400, code, message, field, details)

export const unauthenticated = (code: string, message: string) => new ApiFailure(401, code, message)

export const forbidden = (code: string, message: string, details?: Record<string, unknown>) =>
  new ApiFailure(403, code, message, undefined, details)

export const notFound = (message = 'No such record') => new ApiFailure(404, 'NOT_FOUND', message)

export const conflict = (code: string, message: string, details?: Record<string, unknown>) =>
  new ApiFailure(409, code, message, undefined, details)

/* -------------------------------------------------------------------------- */
/* Routing                                                                     */
/* -------------------------------------------------------------------------- */

interface Route {
  method: string
  /** Compiled from a `/v1/employees/:id/form` style pattern. */
  matcher: RegExp
  names: readonly string[]
  handler: DemoHandler
}

export class Router {
  private readonly routes: Route[] = []

  /**
   * Registers one endpoint.
   *
   * The pattern is written exactly as `spec/openapi.yaml` writes the path, `:id` standing in for
   * `{id}`. Keeping the two textually comparable is the cheapest defence there is against a demo
   * that answers a path the real server does not have — a mismatch is then a diff, not an
   * investigation.
   */
  on(method: string, pattern: string, handler: DemoHandler): this {
    const names: string[] = []
    const source = pattern
      .split('/')
      .map((segment) => {
        if (!segment.startsWith(':')) return escapeForRegExp(segment)
        names.push(segment.slice(1))
        return '([^/]+)'
      })
      .join('/')
    this.routes.push({ method, matcher: new RegExp(`^${source}$`), names, handler })
    return this
  }

  match(method: string, path: string): { handler: DemoHandler; params: Record<string, string> } | null {
    for (const route of this.routes) {
      if (route.method !== method) continue
      const found = route.matcher.exec(path)
      if (found === null) continue
      const params: Record<string, string> = {}
      route.names.forEach((name, index) => {
        params[name] = decodeURIComponent(found[index + 1] ?? '')
      })
      return { handler: route.handler, params }
    }
    return null
  }
}

function escapeForRegExp(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

/**
 * A path parameter the route pattern guarantees exists.
 *
 * The compiler cannot know that, and defaulting to an empty string would turn a routing bug into a
 * 404 on a record id of `""` — which reads as "that employee does not exist" and sends whoever is
 * debugging it to look at the fixtures.
 */
export function pathParam(request: DemoRequest, name: string): string {
  const value = request.params[name]
  if (value === undefined) throw new Error(`Route matched without a '${name}' parameter`)
  return value
}

/* -------------------------------------------------------------------------- */
/* Reading and writing                                                         */
/* -------------------------------------------------------------------------- */

/** 256 KiB. Nothing this API accepts is close to it; the cap is here so a stray upload cannot pin the dev server. */
const MAX_BODY_BYTES = 262_144

function readRawBody(request: IncomingMessage): Promise<Buffer> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = []
    let size = 0
    request.on('data', (chunk: Buffer) => {
      size += chunk.length
      if (size > MAX_BODY_BYTES) {
        request.destroy()
        reject(badRequest('MALFORMED_REQUEST', 'Request body is too large'))
        return
      }
      chunks.push(chunk)
    })
    request.on('end', () => {
      resolve(Buffer.concat(chunks))
    })
    request.on('error', reject)
  })
}

export async function readJsonBody(request: IncomingMessage): Promise<unknown> {
  const text = (await readRawBody(request)).toString('utf8').trim()
  if (text === '') return undefined
  try {
    return JSON.parse(text) as unknown
  } catch {
    throw badRequest('MALFORMED_REQUEST', 'Request body is not valid JSON')
  }
}

/**
 * An object body, or a refusal.
 *
 * `MALFORMED_REQUEST` rather than `VALIDATION_FAILED` because a body that is not an object is not
 * a field that failed a rule — there is nothing to name in `field`, and telling the client
 * otherwise sends it looking for an input to highlight.
 */
export function objectBody(request: DemoRequest): Record<string, unknown> {
  const { body } = request
  if (typeof body !== 'object' || body === null || Array.isArray(body)) {
    throw badRequest('MALFORMED_REQUEST', 'Expected a JSON object body')
  }
  return body as Record<string, unknown>
}

export function stringField(source: Record<string, unknown>, key: string): string | undefined {
  const value = source[key]
  return typeof value === 'string' ? value : undefined
}

export function send(response: ServerResponse, reply: DemoReply, requestId: string): void {
  response.setHeader('X-Request-Id', requestId)
  // The console is served same-origin through the dev server, so there is no CORS story to tell
  // here — and inventing one would make the demo permissive where production is not.
  if (reply.status === 204 || reply.body === undefined) {
    response.statusCode = reply.status
    response.end()
    return
  }
  const payload = JSON.stringify(reply.body)
  response.statusCode = reply.status
  response.setHeader('Content-Type', 'application/json; charset=utf-8')
  response.setHeader('Content-Length', Buffer.byteLength(payload))
  response.end(payload)
}

/**
 * Renders a failure as `ApiErrorResponse`.
 *
 * Typed against the generated model rather than assembled freehand, so a field the console reads
 * cannot quietly go missing. Nulls are omitted exactly as the server's `@JsonInclude(NON_NULL)`
 * omits them — a `"field": null` on the wire would be read by `extractFieldViolations` as a
 * violation on a field named `null`.
 */
export function sendFailure(response: ServerResponse, failure: unknown, requestId: string): void {
  const api =
    failure instanceof ApiFailure
      ? failure
      : new ApiFailure(500, 'INTERNAL_ERROR', 'The demo transport threw. See the dev-server console.')

  const body: ApiErrorResponse = {
    error: {
      code: api.code,
      message: api.message,
      ...(api.field !== undefined ? { field: api.field } : {}),
      ...(api.details !== undefined ? { details: api.details } : {}),
      requestId,
    },
  }
  send(response, { status: api.status, body }, requestId)
}

let requestCounter = 0

/** `req_000041`. Short, ordered and greppable — it only has to correlate one console line. */
export function nextRequestId(): string {
  requestCounter += 1
  return `req_${requestCounter.toString().padStart(6, '0')}`
}
