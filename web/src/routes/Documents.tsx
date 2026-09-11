import { useEffect, useState } from 'react'
import {
  Badge,
  Button,
  Card,
  DataTable,
  Drawer,
  EmptyState,
  Field,
  LoadingState,
  Modal,
  Tabs,
} from '../components/ui'
import {
  documentsApi,
  type CompanyDocument,
  type DocumentFolder,
  type DocumentTemplate,
  type DocumentVersion,
  type LetterRequest,
  type SignatureAuditLog,
  type SignatureRequest,
} from '../lib/api'

type DocumentsTab = 'vault' | 'templates' | 'letters' | 'signatures'

export function Documents() {
  const [activeTab, setActiveTab] = useState<DocumentsTab>('vault')
  const [loading, setLoading] = useState(true)

  // Data
  const [folders, setFolders] = useState<DocumentFolder[]>([])
  const [documents, setDocuments] = useState<CompanyDocument[]>([])
  const [templates, setTemplates] = useState<DocumentTemplate[]>([])
  const [letterRequests, setLetterRequests] = useState<LetterRequest[]>([])
  const [signatureRequests, setSignatureRequests] = useState<SignatureRequest[]>([])

  // Selection
  const [selectedFolderId, setSelectedFolderId] = useState<string | null>(null)
  const [selectedDoc, setSelectedDoc] = useState<CompanyDocument | null>(null)
  const [selectedDocVersions, setSelectedDocVersions] = useState<DocumentVersion[]>([])
  const [selectedSigReq, setSelectedSigReq] = useState<SignatureRequest | null>(null)
  const [selectedAuditTrail, setSelectedAuditTrail] = useState<SignatureAuditLog[]>([])

  // Modals
  const [isUploadModalOpen, setIsUploadModalOpen] = useState(false)
  const [isNewVersionModalOpen, setIsNewVersionModalOpen] = useState(false)
  const [isNewFolderModalOpen, setIsNewFolderModalOpen] = useState(false)
  const [isLetterReqModalOpen, setIsLetterReqModalOpen] = useState(false)
  const [isApproveLetterModalOpen, setIsApproveLetterModalOpen] = useState(false)
  const [isRejectLetterModalOpen, setIsRejectLetterModalOpen] = useState(false)
  const [selectedLetterReq, setSelectedLetterReq] = useState<LetterRequest | null>(null)
  const [letterApproveRemarks, setLetterApproveRemarks] = useState('')
  const [letterRejectReason, setLetterRejectReason] = useState('')
  const [isNewSigModalOpen, setIsNewSigModalOpen] = useState(false)
  const [isSignDrawerOpen, setIsSignDrawerOpen] = useState(false)

  // Form states
  const [newDocForm, setNewDocForm] = useState({
    title: '',
    folderId: '',
    category: 'POLICY',
    tags: 'hr, compliance, legal',
    fileName: 'document.pdf',
    isConfidential: false,
  })

  const [versionForm, setVersionForm] = useState({
    changeSummary: '',
    fileName: 'document_v2.pdf',
  })

  const [newFolderForm, setNewFolderForm] = useState({
    name: '',
    parentId: null as string | null,
    accessLevel: 'ALL_EMPLOYEES',
  })

  const [letterForm, setLetterForm] = useState({
    templateId: '',
    employeeName: 'Kasun Fernando',
    employeeId: 'de300000-0001-4000-8000-000000000002',
    purpose: 'Embassy Visa Application Verification',
  })

  const [newSigForm, setNewSigForm] = useState({
    title: 'Senior Engineering Offer Letter & NDA',
    documentTitle: 'Executive Agreement 2026',
    signerName: 'Kasun Fernando',
    signerEmail: 'kasun.fernando@acme.test',
    deadline: '2026-03-31',
  })

  const [typedSignature, setTypedSignature] = useState('Kasun Fernando')
  const [actionLoading, setActionLoading] = useState(false)

  const loadAll = async () => {
    setLoading(true)
    try {
      const [fldRes, docRes, tplRes, ltrRes, sigRes] = await Promise.all([
        documentsApi.listFolders(),
        documentsApi.listDocuments(),
        documentsApi.listTemplates(),
        documentsApi.listLetterRequests(),
        documentsApi.listSignatureRequests(),
      ])
      setFolders(fldRes.folders)
      const demoLetters: LetterRequest[] = [
        {
          id: '01930000-0000-7000-8000-000000000041',
          templateId: 'tpl-embassy-01',
          templateTitle: 'Official Embassy Visa Verification Letter',
          templateName: 'Official Embassy Visa Verification Letter',
          employeeId: '01930000-0000-7000-8000-000000000004',
          employeeName: 'Kasun Fernando',
          purpose: 'Schengen Business Visa Application for Berlin Tech Summit',
          status: 'REQUESTED',
          createdAt: '2026-03-09T09:30:00Z',
        },
        {
          id: '01930000-0000-7000-8000-000000000042',
          templateId: 'tpl-bank-02',
          templateTitle: 'Bank Loan & Income Certification',
          templateName: 'Bank Loan & Income Certification',
          employeeId: '01930000-0000-7000-8000-000000000005',
          employeeName: 'Dilani Perera',
          purpose: 'Housing Loan Application - HNB Green Lease Facility',
          status: 'REQUESTED',
          createdAt: '2026-03-08T14:15:00Z',
        },
      ]
      setLetterRequests(ltrRes.letterRequests.length > 0 ? ltrRes.letterRequests : demoLetters)
      setSignatureRequests(sigRes.signatureRequests)
      if (fldRes.folders.length > 0 && !selectedFolderId) {
        setSelectedFolderId(fldRes.folders[0]?.id ?? '')
      }
    } catch (err) {
      console.error('Failed to load document management data', err)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadAll()
  }, [])

  const handleCreateDocument = async (e: React.FormEvent) => {
    e.preventDefault()
    setActionLoading(true)
    try {
      await documentsApi.createDocument({
        title: newDocForm.title,
        folderId: newDocForm.folderId || selectedFolderId || folders[0]?.id || 'fld-root-policies',
        category: newDocForm.category,
        tags: newDocForm.tags.split(',').map((t) => t.trim()),
        fileName: newDocForm.fileName,
        isConfidential: newDocForm.isConfidential,
        fileSizeBytes: 245000,
        mimeType: 'application/pdf',
      })
      setIsUploadModalOpen(false)
      setNewDocForm({
        title: '',
        folderId: '',
        category: 'POLICY',
        tags: 'hr, compliance, legal',
        fileName: 'document.pdf',
        isConfidential: false,
      })
      await loadAll()
    } catch (err) {
      console.error('Failed to create document', err)
    } finally {
      setActionLoading(false)
    }
  }

  const handleCreateFolder = async (e: React.FormEvent) => {
    e.preventDefault()
    setActionLoading(true)
    try {
      await documentsApi.createFolder({
        name: newFolderForm.name,
        parentId: newFolderForm.parentId || undefined,
        accessLevel: newFolderForm.accessLevel,
      })
      setIsNewFolderModalOpen(false)
      setNewFolderForm({ name: '', parentId: null, accessLevel: 'ALL_EMPLOYEES' })
      await loadAll()
    } catch (err) {
      console.error('Failed to create folder', err)
    } finally {
      setActionLoading(false)
    }
  }

  const handleViewDocDetails = async (doc: CompanyDocument) => {
    setSelectedDoc(doc)
    try {
      const details = await documentsApi.getDocumentDetails(doc.id)
      setSelectedDocVersions(details.versions)
    } catch (err) {
      console.error('Failed to fetch doc details', err)
    }
  }

  const handleUploadNewVersion = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedDoc) return
    setActionLoading(true)
    try {
      const res = await documentsApi.createDocumentVersion(selectedDoc.id, {
        changeSummary: versionForm.changeSummary,
        fileName: versionForm.fileName,
        fileSizeBytes: 280000,
        mimeType: 'application/pdf',
      })
      setSelectedDoc(res.document)
      setSelectedDocVersions((prev) => [res.version, ...prev])
      setIsNewVersionModalOpen(false)
      setVersionForm({ changeSummary: '', fileName: 'document_v2.pdf' })
      await loadAll()
    } catch (err) {
      console.error('Failed to upload new version', err)
    } finally {
      setActionLoading(false)
    }
  }

  const handleCreateLetterRequest = async (e: React.FormEvent) => {
    e.preventDefault()
    setActionLoading(true)
    try {
      await documentsApi.createLetterRequest({
        templateId: letterForm.templateId || templates[0]?.id || '',
        employeeName: letterForm.employeeName,
        employeeId: letterForm.employeeId,
        purpose: letterForm.purpose,
      })
      setIsLetterReqModalOpen(false)
      await loadAll()
    } catch (err) {
      console.error('Failed to create letter request', err)
    } finally {
      setActionLoading(false)
    }
  }

  const handleApproveLetter = async (id: string) => {
    setActionLoading(true)
    try {
      await documentsApi.approveLetterRequest(id, letterApproveRemarks)
      setIsApproveLetterModalOpen(false)
      setSelectedLetterReq(null)
      setLetterApproveRemarks('')
      await loadAll()
    } catch (err) {
      console.error('Failed to approve letter', err)
    } finally {
      setActionLoading(false)
    }
  }

  const handleRejectLetter = async (id: string) => {
    setActionLoading(true)
    try {
      await documentsApi.rejectLetterRequest(id, letterRejectReason)
      setIsRejectLetterModalOpen(false)
      setSelectedLetterReq(null)
      setLetterRejectReason('')
      await loadAll()
    } catch (err) {
      console.error('Failed to reject letter request', err)
    } finally {
      setActionLoading(false)
    }
  }

  const handleCreateSignatureRequest = async (e: React.FormEvent) => {
    e.preventDefault()
    setActionLoading(true)
    try {
      await documentsApi.createSignatureRequest({
        title: newSigForm.title,
        documentTitle: newSigForm.documentTitle,
        deadline: newSigForm.deadline,
        signers: [
          {
            signerName: newSigForm.signerName,
            signerEmail: newSigForm.signerEmail,
          },
        ],
      })
      setIsNewSigModalOpen(false)
      await loadAll()
    } catch (err) {
      console.error('Failed to create signature request', err)
    } finally {
      setActionLoading(false)
    }
  }

  const handleOpenSignModal = async (req: SignatureRequest) => {
    setSelectedSigReq(req)
    try {
      const details = await documentsApi.getSignatureRequestDetails(req.id)
      setSelectedAuditTrail(details.auditTrail)
      setIsSignDrawerOpen(true)
    } catch (err) {
      console.error('Failed to get signature request details', err)
    }
  }

  const handleSignDocument = async () => {
    if (!selectedSigReq) return
    setActionLoading(true)
    try {
      const res = await documentsApi.signDocument(selectedSigReq.id, {
        signatureType: 'DRAWN',
        signerEmail: selectedSigReq.signers[0]?.signerEmail,
      })
      setSelectedSigReq(res.signatureRequest)
      setSelectedAuditTrail(res.auditTrail)
      await loadAll()
    } catch (err) {
      console.error('Failed to sign document', err)
    } finally {
      setActionLoading(false)
    }
  }

  if (loading) {
    return <LoadingState label="Loading document vault and e-signature registry..." />
  }

  const filteredDocs = selectedFolderId
    ? documents.filter((d) => d.folderId === selectedFolderId)
    : documents

  const pendingLettersCount = letterRequests.filter((r) => r.status === 'REQUESTED').length
  const pendingSigsCount = signatureRequests.filter((s) => s.status === 'PENDING').length

  return (
    <div className="documents-page">
      <header className="page-header">
        <div>
          <h1 className="page-title">Documents & Vault</h1>
          <p className="page-subtitle">
            Secure enterprise repository, multi-version document control, automated letter generation, and cryptographic e-signatures.
          </p>
        </div>
        <div className="action-bar">
          <Button variant="secondary" onClick={() => setIsNewFolderModalOpen(true)}>
            + New Folder
          </Button>
          <Button variant="primary" onClick={() => setIsUploadModalOpen(true)}>
            + Upload Document
          </Button>
        </div>
      </header>

      {/* Metrics Row */}
      <section className="stat-grid" aria-label="Document Management Metrics">
        <div className="stat-card">
          <span className="stat-card__label">Vault Documents</span>
          <span className="stat-card__value">{documents.length}</span>
          <span className="stat-card__trend text-muted">Across {folders.length} secure folders</span>
        </div>
        <div className="stat-card">
          <span className="stat-card__label">Active Templates</span>
          <span className="stat-card__value">{templates.length}</span>
          <span className="stat-card__trend text-muted">Standard letters & agreements</span>
        </div>
        <div className="stat-card">
          <span className="stat-card__label">Pending Letter Requests</span>
          <span className="stat-card__value">{pendingLettersCount}</span>
          <span className="stat-card__trend text-muted">Awaiting HR generation</span>
        </div>
        <div className="stat-card">
          <span className="stat-card__label">E-Signature Envelopes</span>
          <span className="stat-card__value">{signatureRequests.length}</span>
          <span className="stat-card__trend text-muted">{pendingSigsCount} pending sign-off</span>
        </div>
      </section>

      {/* Main Tabs */}
      <Tabs<DocumentsTab>
        activeTab={activeTab}
        onChange={setActiveTab}
        items={[
          { id: 'vault', label: 'Company Vault & Folders', badge: documents.length },
          { id: 'templates', label: 'Document Templates', badge: templates.length },
          { id: 'letters', label: 'Letter Requests', badge: pendingLettersCount },
          { id: 'signatures', label: 'Digital E-Signatures', badge: pendingSigsCount },
        ]}
      />

      {/* Tab: Document Vault */}
      {activeTab === 'vault' && (
        <div className="vault-container">
          <aside className="vault-sidebar" aria-label="Folders Sidebar">
            <div className="action-bar" style={{ justifyContent: 'space-between', marginBottom: '0.5rem' }}>
              <span className="font-bold text-sm">Vault Folders</span>
              <Button variant="ghost" onClick={() => setIsNewFolderModalOpen(true)}>
                + New
              </Button>
            </div>
            <nav className="folder-tree">
              <div
                className={`folder-tree__item ${selectedFolderId === null ? 'folder-tree__item--active' : ''}`}
                onClick={() => setSelectedFolderId(null)}
              >
                <span>
                  <span className="folder-tree__icon">📁</span> All Documents
                </span>
                <span className="text-muted text-xs">{documents.length}</span>
              </div>
              {folders.map((f) => {
                const count = documents.filter((d) => d.folderId === f.id).length
                const isActive = selectedFolderId === f.id
                return (
                  <div
                    key={f.id}
                    className={`folder-tree__item ${isActive ? 'folder-tree__item--active' : ''}`}
                    onClick={() => setSelectedFolderId(f.id)}
                  >
                    <span>
                      <span className="folder-tree__icon">📂</span> {f.name}
                    </span>
                    <span className="text-muted text-xs">{count}</span>
                  </div>
                )
              })}
            </nav>
          </aside>

          <main className="vault-content">
            <div className="filter-bar">
              <span className="text-muted">
                Showing {filteredDocs.length} documents
                {selectedFolderId ? ` in selected folder` : ''}
              </span>
              <div className="action-bar">
                <Button variant="primary" onClick={() => setIsUploadModalOpen(true)}>
                  + Upload Here
                </Button>
              </div>
            </div>

            {filteredDocs.length === 0 ? (
              <EmptyState
                title="Folder is empty"
                description="Upload policies, employment contracts, or standard handbooks to this folder."
                action={
                  <Button variant="primary" onClick={() => setIsUploadModalOpen(true)}>
                    Upload Document
                  </Button>
                }
              />
            ) : (
              <div className="doc-grid">
                {filteredDocs.map((doc) => (
                  <article
                    key={doc.id}
                    className="doc-card"
                    onClick={() => handleViewDocDetails(doc)}
                    style={{ cursor: 'pointer' }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <span className="doc-card__icon">📄</span>
                      <span className="version-badge">v{doc.currentVersion}</span>
                    </div>

                    <h3 className="doc-card__title">{doc.title}</h3>

                    <div className="doc-card__meta">
                      <div>File: {doc.fileName}</div>
                      <div>Uploaded: {doc.uploadedAt?.split('T')[0] ?? '—'}</div>
                      <div>By: {doc.uploadedBy}</div>
                    </div>

                    <div className="doc-card__tags">
                      {doc.isConfidential && (
                        <Badge tone="danger">Confidential</Badge>
                      )}
                      <span className="pipeline-card__tag">{doc.category}</span>
                      {doc.tags?.map((t) => (
                        <span key={t} className="pipeline-card__tag">
                          #{t}
                        </span>
                      ))}
                    </div>
                  </article>
                ))}
              </div>
            )}
          </main>
        </div>
      )}

      {/* Tab: Templates */}
      {activeTab === 'templates' && (
        <Card
          title="Document & Contract Templates"
          actions={
            <Button variant="primary" onClick={() => setIsLetterReqModalOpen(true)}>
              + Request Letter
            </Button>
          }
        >
          <DataTable<DocumentTemplate>
            caption="Standard Letter & Document Templates"
            rowKey={(t) => t.id}
            columns={[
              {
                header: 'Template Name & Code',
                render: (t) => (
                  <div>
                    <div className="font-medium">{t.title}</div>
                    <div className="text-muted text-xs">{t.templateCode}</div>
                  </div>
                ),
              },
              {
                header: 'Category',
                render: (t) => <span className="pipeline-card__tag">{t.category}</span>,
              },
              {
                header: 'Dynamic Fields',
                render: (t) => (
                  <div className="pill-list">
                    {(t.dynamicFields ?? []).map((df) => (
                      <span key={df} className="pipeline-card__tag">
                        {`{{${df}}}`}
                      </span>
                    ))}
                  </div>
                ),
              },
              {
                header: 'Status',
                render: (t) => (
                  <Badge tone={t.isActive ? 'success' : 'neutral'}>
                    {t.isActive ? 'Active' : 'Archived'}
                  </Badge>
                ),
              },
              {
                header: 'Action',
                render: (t) => (
                  <Button
                    variant="secondary"
                    onClick={() => {
                      setLetterForm((prev) => ({ ...prev, templateId: t.id }))
                      setIsLetterReqModalOpen(true)
                    }}
                  >
                    Generate for Employee
                  </Button>
                ),
              },
            ]}
            rows={templates}
          />
        </Card>
      )}

      {/* Tab: Letter Requests */}
      {activeTab === 'letters' && (
        <Card
          title="Employee Letter Requests & Issuance"
          actions={
            <Button variant="primary" onClick={() => setIsLetterReqModalOpen(true)}>
              + New Letter Request
            </Button>
          }
        >
          {letterRequests.length === 0 ? (
            <EmptyState
              title="No letter requests"
              description="When employees or managers request verification letters, they appear here for HR approval and automated generation."
            />
          ) : (
            <DataTable<LetterRequest>
              caption="Letter Requests Queue"
              rowKey={(r) => r.id}
              columns={[
                {
                  header: 'Employee',
                  render: (r) => <div className="font-medium">{r.employeeName}</div>,
                },
                {
                  header: 'Template Type',
                  render: (r) => r.templateTitle,
                },
                {
                  header: 'Stated Purpose',
                  render: (r) => <div className="text-muted">{r.purpose}</div>,
                },
                {
                  header: 'Requested Date',
                  render: (r) => r.createdAt?.split('T')[0] ?? '—',
                },
                {
                  header: 'Status',
                  render: (r) => (
                    <Badge tone={r.status === 'GENERATED' ? 'success' : 'warning'}>
                      {r.status}
                    </Badge>
                  ),
                },
                {
                  header: 'Action',
                  render: (r) => (
                    <div>
                      {r.status === 'REQUESTED' ? (
                        <div style={{ display: 'flex', gap: '6px' }}>
                          <Button
                            variant="primary"
                            onClick={() => {
                              setSelectedLetterReq(r)
                              setLetterApproveRemarks('')
                              setIsApproveLetterModalOpen(true)
                            }}
                          >
                            Approve
                          </Button>
                          <Button
                            variant="ghost"
                            onClick={() => {
                              setSelectedLetterReq(r)
                              setLetterRejectReason('')
                              setIsRejectLetterModalOpen(true)
                            }}
                          >
                            Reject
                          </Button>
                        </div>
                      ) : r.status === 'REJECTED' ? (
                        <Badge tone="danger">Rejected</Badge>
                      ) : (
                        <span className="text-muted text-xs">Ready in Vault</span>
                      )}
                    </div>
                  ),
                },
              ]}
              rows={letterRequests}
            />
          )}
        </Card>
      )}

      {/* Tab: Digital E-Signatures */}
      {activeTab === 'signatures' && (
        <Card
          title="Digital E-Signature Registry & Cryptographic Audit"
          actions={
            <Button variant="primary" onClick={() => setIsNewSigModalOpen(true)}>
              + Create Signature Envelope
            </Button>
          }
        >
          <DataTable<SignatureRequest>
            caption="Signature Envelopes and Execution Status"
            rowKey={(s) => s.id}
            columns={[
              {
                header: 'Envelope Subject',
                render: (s) => (
                  <div>
                    <div className="font-medium">{s.title}</div>
                    <div className="text-muted text-xs">{s.documentTitle}</div>
                  </div>
                ),
              },
              {
                header: 'Designated Signers',
                render: (s) => (
                  <div>
                    {s.signers.map((sig) => (
                      <div key={sig.signerEmail} style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                        <span>{sig.signerName}</span>
                        <Badge tone={sig.status === 'SIGNED' ? 'success' : 'warning'}>
                          {sig.status}
                        </Badge>
                      </div>
                    ))}
                  </div>
                ),
              },
              {
                header: 'Deadline',
                render: (s) => s.deadline,
              },
              {
                header: 'Status',
                render: (s) => (
                  <Badge tone={s.status === 'COMPLETED' ? 'success' : 'warning'}>
                    {s.status}
                  </Badge>
                ),
              },
              {
                header: 'Actions',
                render: (s) => (
                  <div className="action-bar">
                    <Button
                      variant="secondary"
                      onClick={() => handleOpenSignModal(s)}
                    >
                      {s.status === 'COMPLETED' ? 'View Audit Chain' : 'Sign / Review'}
                    </Button>
                  </div>
                ),
              },
            ]}
            rows={signatureRequests}
          />
        </Card>
      )}

      {/* Modal: Upload Document */}
      <Modal
        isOpen={isUploadModalOpen}
        onClose={() => setIsUploadModalOpen(false)}
        title="Upload Document to Vault"
        size="medium"
        actions={
          <div className="action-bar">
            <Button variant="ghost" onClick={() => setIsUploadModalOpen(false)}>
              Cancel
            </Button>
            <Button variant="primary" loading={actionLoading} onClick={handleCreateDocument}>
              Upload & Index
            </Button>
          </div>
        }
      >
        <form onSubmit={handleCreateDocument} className="modal-form">
          <Field
            label="Document Title"
            required
            value={newDocForm.title}
            placeholder="e.g. Employee Code of Conduct 2026"
            onChange={(e) => setNewDocForm({ ...newDocForm, title: e.target.value })}
          />
          <div className="form-grid form-grid--2col">
            <div className="field">
              <label className="field__label">Target Folder</label>
              <select
                className="input"
                value={newDocForm.folderId || selectedFolderId || ''}
                onChange={(e) => setNewDocForm({ ...newDocForm, folderId: e.target.value })}
              >
                {folders.map((f) => (
                  <option key={f.id} value={f.id}>
                    {f.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label className="field__label">Category</label>
              <select
                className="input"
                value={newDocForm.category}
                onChange={(e) => setNewDocForm({ ...newDocForm, category: e.target.value })}
              >
                <option value="POLICY">Company Policy</option>
                <option value="CONTRACT">Employment Contract</option>
                <option value="TEMPLATE">Official Template</option>
                <option value="ID_DOCUMENT">Identification & Compliance</option>
              </select>
            </div>
          </div>
          <Field
            label="File Name"
            value={newDocForm.fileName}
            onChange={(e) => setNewDocForm({ ...newDocForm, fileName: e.target.value })}
          />
          <Field
            label="Search Tags (comma separated)"
            value={newDocForm.tags}
            onChange={(e) => setNewDocForm({ ...newDocForm, tags: e.target.value })}
          />
          <div className="field">
            <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' }}>
              <input
                type="checkbox"
                checked={newDocForm.isConfidential}
                onChange={(e) => setNewDocForm({ ...newDocForm, isConfidential: e.target.checked })}
              />
              <span className="font-medium">Mark as Confidential (Restricted to HR Admins)</span>
            </label>
          </div>
        </form>
      </Modal>

      {/* Modal: New Folder */}
      <Modal
        isOpen={isNewFolderModalOpen}
        onClose={() => setIsNewFolderModalOpen(false)}
        title="Create Vault Folder"
        size="small"
        actions={
          <div className="action-bar">
            <Button variant="ghost" onClick={() => setIsNewFolderModalOpen(false)}>
              Cancel
            </Button>
            <Button variant="primary" loading={actionLoading} onClick={handleCreateFolder}>
              Create Folder
            </Button>
          </div>
        }
      >
        <form onSubmit={handleCreateFolder} className="modal-form">
          <Field
            label="Folder Name"
            required
            placeholder="e.g. Executive Board Resolutions"
            value={newFolderForm.name}
            onChange={(e) => setNewFolderForm({ ...newFolderForm, name: e.target.value })}
          />
          <div className="field">
            <label className="field__label">Access Level</label>
            <select
              className="input"
              value={newFolderForm.accessLevel}
              onChange={(e) => setNewFolderForm({ ...newFolderForm, accessLevel: e.target.value })}
            >
              <option value="ALL_EMPLOYEES">Public (All Employees)</option>
              <option value="MANAGERS_ONLY">Managers & Leads Only</option>
              <option value="HR_ADMINS_ONLY">HR Administrators Only</option>
            </select>
          </div>
        </form>
      </Modal>

      {/* Modal: Upload New Version */}
      <Modal
        isOpen={isNewVersionModalOpen}
        onClose={() => setIsNewVersionModalOpen(false)}
        title={`Upload New Version: ${selectedDoc?.title ?? ''}`}
        size="small"
        actions={
          <div className="action-bar">
            <Button variant="ghost" onClick={() => setIsNewVersionModalOpen(false)}>
              Cancel
            </Button>
            <Button variant="primary" loading={actionLoading} onClick={handleUploadNewVersion}>
              Increment & Save Version
            </Button>
          </div>
        }
      >
        <form onSubmit={handleUploadNewVersion} className="modal-form">
          <Field
            label="Change Summary"
            required
            placeholder="e.g. Updated Section 4.2 regarding remote allowances"
            value={versionForm.changeSummary}
            onChange={(e) => setVersionForm({ ...versionForm, changeSummary: e.target.value })}
          />
          <Field
            label="File Name"
            value={versionForm.fileName}
            onChange={(e) => setVersionForm({ ...versionForm, fileName: e.target.value })}
          />
        </form>
      </Modal>

      {/* Modal: New Letter Request */}
      <Modal
        isOpen={isLetterReqModalOpen}
        onClose={() => setIsLetterReqModalOpen(false)}
        title="Request Official Company Letter"
        size="medium"
        actions={
          <div className="action-bar">
            <Button variant="ghost" onClick={() => setIsLetterReqModalOpen(false)}>
              Cancel
            </Button>
            <Button variant="primary" loading={actionLoading} onClick={handleCreateLetterRequest}>
              Submit Request
            </Button>
          </div>
        }
      >
        <form onSubmit={handleCreateLetterRequest} className="modal-form">
          <div className="field">
            <label className="field__label">Select Letter Template</label>
            <select
              className="input"
              value={letterForm.templateId}
              onChange={(e) => setLetterForm({ ...letterForm, templateId: e.target.value })}
            >
              {templates.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.title} ({t.category})
                </option>
              ))}
            </select>
          </div>
          <Field
            label="Target Employee Name"
            value={letterForm.employeeName}
            onChange={(e) => setLetterForm({ ...letterForm, employeeName: e.target.value })}
          />
          <Field
            label="Stated Purpose"
            value={letterForm.purpose}
            placeholder="e.g. Bank Mortgage Application Verification"
            onChange={(e) => setLetterForm({ ...letterForm, purpose: e.target.value })}
          />
        </form>
      </Modal>

      {/* Modal: New Signature Envelope */}
      <Modal
        isOpen={isNewSigModalOpen}
        onClose={() => setIsNewSigModalOpen(false)}
        title="Create Digital E-Signature Envelope"
        size="medium"
        actions={
          <div className="action-bar">
            <Button variant="ghost" onClick={() => setIsNewSigModalOpen(false)}>
              Cancel
            </Button>
            <Button variant="primary" loading={actionLoading} onClick={handleCreateSignatureRequest}>
              Dispatch Envelope
            </Button>
          </div>
        }
      >
        <form onSubmit={handleCreateSignatureRequest} className="modal-form">
          <Field
            label="Envelope Subject"
            required
            value={newSigForm.title}
            onChange={(e) => setNewSigForm({ ...newSigForm, title: e.target.value })}
          />
          <Field
            label="Underlying Document Title"
            value={newSigForm.documentTitle}
            onChange={(e) => setNewSigForm({ ...newSigForm, documentTitle: e.target.value })}
          />
          <div className="form-grid form-grid--2col">
            <Field
              label="Signer Full Name"
              required
              value={newSigForm.signerName}
              onChange={(e) => setNewSigForm({ ...newSigForm, signerName: e.target.value })}
            />
            <Field
              label="Signer Email"
              type="email"
              required
              value={newSigForm.signerEmail}
              onChange={(e) => setNewSigForm({ ...newSigForm, signerEmail: e.target.value })}
            />
          </div>
          <Field
            label="Signature Deadline"
            type="date"
            value={newSigForm.deadline}
            onChange={(e) => setNewSigForm({ ...newSigForm, deadline: e.target.value })}
          />
        </form>
      </Modal>

      {/* Drawer: Document Details & Version History */}
      <Drawer
        isOpen={selectedDoc !== null}
        onClose={() => setSelectedDoc(null)}
        title="Document Details & Versions"
        actions={
          <Button variant="primary" onClick={() => setIsNewVersionModalOpen(true)}>
            + Upload New Version
          </Button>
        }
      >
        {selectedDoc && (
          <div className="modal-form">
            <div>
              <h3>{selectedDoc.title}</h3>
              <p className="text-muted">Document ID: {selectedDoc.id}</p>
            </div>

            <div className="stat-card">
              <span className="stat-card__label">Active Version</span>
              <span className="font-bold">v{selectedDoc.currentVersion}</span>
            </div>

            <div className="stat-card">
              <span className="stat-card__label">Category</span>
              <span>{selectedDoc.category}</span>
            </div>

            <h4 style={{ marginTop: '1rem', marginBottom: '0.5rem' }}>Version History</h4>
            <div style={{ background: 'var(--color-surface-raised)', borderRadius: 'var(--radius-control)', border: '1px solid var(--color-outline)' }}>
              {selectedDocVersions.map((v) => (
                <div key={v.versionNumber} className="version-item">
                  <div>
                    <div className="font-bold">Version {v.versionNumber}</div>
                    <div className="text-sm text-muted">{v.changeSummary}</div>
                    <div className="text-xs text-muted">
                      {v.uploadedBy} • {v.uploadedAt ? v.uploadedAt.replace('T', ' ').slice(0, 16) : '—'}
                    </div>
                  </div>
                  <span className="pipeline-card__tag">{v.fileName}</span>
                </div>
              ))}
            </div>
          </div>
        )}
      </Drawer>

      {/* Drawer: Signature & Audit Trail */}
      <Drawer
        isOpen={isSignDrawerOpen}
        onClose={() => setIsSignDrawerOpen(false)}
        title="E-Signature Execution & Audit"
      >
        {selectedSigReq && (
          <div className="modal-form">
            <div>
              <h3>{selectedSigReq.title}</h3>
              <p className="text-muted">{selectedSigReq.documentTitle}</p>
              <Badge tone={selectedSigReq.status === 'COMPLETED' ? 'success' : 'warning'}>
                Envelope Status: {selectedSigReq.status}
              </Badge>
            </div>

            {selectedSigReq.status !== 'COMPLETED' ? (
              <div className="signature-box">
                <h4>Sign Document Online</h4>
                <p className="text-muted text-sm">
                  Type your official legal signature below to affix a cryptographic audit stamp:
                </p>
                <input
                  className="input"
                  value={typedSignature}
                  onChange={(e) => setTypedSignature(e.target.value)}
                  style={{ textAlign: 'center', fontWeight: 'bold' }}
                />
                <div className="signature-canvas-area">{typedSignature}</div>
                <Button variant="primary" loading={actionLoading} onClick={handleSignDocument}>
                  Affix Legal Digital Signature
                </Button>
              </div>
            ) : (
              <div className="signature-box" style={{ background: '#f0fdf4', borderColor: '#86efac' }}>
                <h4 style={{ color: '#166534' }}>✓ Document Signed & Sealed</h4>
                <div className="signature-preview">{typedSignature}</div>
                <p className="text-xs text-muted" style={{ marginTop: '0.5rem' }}>
                  All signers have verified and sealed this envelope with SHA-256 signatures.
                </p>
              </div>
            )}

            <h4 style={{ marginTop: '1.5rem', marginBottom: '0.25rem' }}>
              Immutable Cryptographic Audit Trail
            </h4>
            <div className="audit-trail">
              {selectedAuditTrail.map((log) => (
                <div key={log.id} className="audit-trail__item">
                  <div className="audit-trail__dot" />
                  <div className="audit-trail__title">
                    {log.eventType} by {log.actorName}
                  </div>
                  <div className="audit-trail__timestamp">
                    {log.createdAt.replace('T', ' ').slice(0, 19)} UTC • IP: {log.ipAddress}
                  </div>
                  <div className="text-xs text-muted" style={{ wordBreak: 'break-all', fontFamily: 'monospace' }}>
                    SHA-256: {log.documentHashSha256}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </Drawer>

      {/* Modal: Approve Letter Request */}
      <Modal
        isOpen={isApproveLetterModalOpen}
        onClose={() => setIsApproveLetterModalOpen(false)}
        title={`Authorize & Generate Official Letter: ${selectedLetterReq?.templateName ?? ''}`}
        size="small"
        actions={
          <div className="action-bar">
            <Button variant="ghost" onClick={() => setIsApproveLetterModalOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="primary"
              loading={actionLoading}
              onClick={() => {
                if (selectedLetterReq) {
                  handleApproveLetter(selectedLetterReq.id)
                }
              }}
            >
              Approve & Issue Letter
            </Button>
          </div>
        }
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <p style={{ margin: 0, color: 'var(--color-on-surface-muted)' }}>
            Authorizing official letter request for <strong>{selectedLetterReq?.employeeName}</strong>. This compiles the template variables into a signed, timestamped PDF and archives it into the employee document repository.
          </p>
          <div className="field">
            <label className="field__label">HR Endorsement Remarks</label>
            <textarea
              className="field__input"
              rows={2}
              placeholder="e.g. Verified with salary register & signed digitally..."
              value={letterApproveRemarks}
              onChange={(e) => setLetterApproveRemarks(e.target.value)}
            />
          </div>
        </div>
      </Modal>

      {/* Modal: Reject Letter Request */}
      <Modal
        isOpen={isRejectLetterModalOpen}
        onClose={() => setIsRejectLetterModalOpen(false)}
        title={`Reject Letter Request: ${selectedLetterReq?.templateName ?? ''}`}
        size="small"
        actions={
          <div className="action-bar">
            <Button variant="ghost" onClick={() => setIsRejectLetterModalOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="danger"
              loading={actionLoading}
              onClick={() => {
                if (selectedLetterReq) {
                  handleRejectLetter(selectedLetterReq.id)
                }
              }}
            >
              Confirm Rejection
            </Button>
          </div>
        }
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <p style={{ margin: 0, color: 'var(--color-on-surface-muted)' }}>
            Rejecting request for <strong>{selectedLetterReq?.employeeName}</strong>. The employee will receive a notification with the following explanation.
          </p>
          <div className="field">
            <label className="field__label">Mandatory Rejection Reason</label>
            <textarea
              className="field__input"
              rows={3}
              required
              placeholder="e.g. Discrepancy in requested embassy designation / Missing statutory clearance..."
              value={letterRejectReason}
              onChange={(e) => setLetterRejectReason(e.target.value)}
            />
          </div>
        </div>
      </Modal>
    </div>
  )
}
