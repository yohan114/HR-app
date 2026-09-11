import { useState } from 'react'
import { Badge, Button, Card } from '@/components/ui'

export type FieldType =
  | 'TEXT'
  | 'MULTILINE_TEXT'
  | 'NUMBER'
  | 'DATE'
  | 'DROPDOWN'
  | 'MULTI_SELECT'
  | 'RADIO'
  | 'CHECKBOX'
  | 'ATTACHMENT'
  | 'EMPLOYEE'
  | 'REFERENCE'
  | 'EMAIL'
  | 'PHONE'

export interface FieldOption {
  value: string
  label: string
}

export interface FieldValidation {
  minLength?: number
  maxLength?: number
  min?: number
  max?: number
  pattern?: string
  patternMessage?: string
}

export interface FormFieldItem {
  id: string
  key: string
  label: string
  type: FieldType
  required: boolean
  editable: boolean
  helpText?: string
  validation?: FieldValidation
  options?: FieldOption[]
  referenceTable?: string
  custom: boolean
}

export interface FormSectionItem {
  id: string
  key: string
  label: string
  fields: FormFieldItem[]
}

export interface FormSchemaState {
  entityType: string
  version: string
  sections: FormSectionItem[]
}

const DEFAULT_EMPLOYEE_SCHEMA: FormSchemaState = {
  entityType: 'employee',
  version: '1.0.0',
  sections: [
    {
      id: 'sec-personal',
      key: 'personal_details',
      label: 'Personal Information',
      fields: [
        {
          id: 'f-1',
          key: 'first_name',
          label: 'First Name',
          type: 'TEXT',
          required: true,
          editable: false,
          custom: false,
          validation: { minLength: 2, maxLength: 50 },
        },
        {
          id: 'f-2',
          key: 'last_name',
          label: 'Last Name',
          type: 'TEXT',
          required: true,
          editable: false,
          custom: false,
          validation: { minLength: 2, maxLength: 50 },
        },
        {
          id: 'f-3',
          key: 'work_email',
          label: 'Work Email',
          type: 'EMAIL',
          required: true,
          editable: false,
          custom: false,
          helpText: 'Official company email address',
        },
        {
          id: 'f-4',
          key: 'nic_passport',
          label: 'National ID / Passport',
          type: 'TEXT',
          required: true,
          editable: true,
          custom: true,
          helpText: 'Government issued national identity number',
          validation: { pattern: '^[0-9]{9}[vVxX]|[0-9]{12}$', patternMessage: 'Enter a valid NIC format' },
        },
      ],
    },
    {
      id: 'sec-emergency',
      key: 'emergency_contact',
      label: 'Emergency Contacts & Next of Kin',
      fields: [
        {
          id: 'f-5',
          key: 'emergency_name',
          label: 'Contact Person Name',
          type: 'TEXT',
          required: true,
          editable: true,
          custom: true,
        },
        {
          id: 'f-6',
          key: 'emergency_phone',
          label: 'Emergency Phone Number',
          type: 'PHONE',
          required: true,
          editable: true,
          custom: true,
          helpText: 'Include international dialing code (+94...)',
        },
        {
          id: 'f-7',
          key: 'relationship',
          label: 'Relationship',
          type: 'DROPDOWN',
          required: true,
          editable: true,
          custom: true,
          options: [
            { value: 'SPOUSE', label: 'Spouse' },
            { value: 'PARENT', label: 'Parent' },
            { value: 'SIBLING', label: 'Sibling' },
            { value: 'OTHER', label: 'Other Relative' },
          ],
        },
      ],
    },
    {
      id: 'sec-custom-dept',
      key: 'custom_operations',
      label: 'Workplace & Health Requirements',
      fields: [
        {
          id: 'f-8',
          key: 'blood_group',
          label: 'Blood Group',
          type: 'DROPDOWN',
          required: false,
          editable: true,
          custom: true,
          options: [
            { value: 'A+', label: 'A Positive (A+)' },
            { value: 'A-', label: 'A Negative (A-)' },
            { value: 'B+', label: 'B Positive (B+)' },
            { value: 'B-', label: 'B Negative (B-)' },
            { value: 'O+', label: 'O Positive (O+)' },
            { value: 'O-', label: 'O Negative (O-)' },
            { value: 'AB+', label: 'AB Positive (AB+)' },
            { value: 'AB-', label: 'AB Negative (AB-)' },
          ],
        },
        {
          id: 'f-9',
          key: 'workstation_preference',
          label: 'Work Arrangement',
          type: 'RADIO',
          required: true,
          editable: true,
          custom: true,
          options: [
            { value: 'ONSITE', label: 'Full Onsite (Headquarters)' },
            { value: 'HYBRID', label: 'Hybrid (3 days office / 2 remote)' },
            { value: 'REMOTE', label: 'Full Remote' },
          ],
        },
        {
          id: 'f-10',
          key: 'medical_declaration',
          label: 'Medical Fitness Certificate',
          type: 'ATTACHMENT',
          required: false,
          editable: true,
          custom: true,
          helpText: 'PDF or scanned image of annual fitness checkup',
        },
      ],
    },
  ],
}

const FIELD_PALETTE: Array<{ type: FieldType; label: string; icon: string }> = [
  { type: 'TEXT', label: 'Single-line Text', icon: '📝' },
  { type: 'MULTILINE_TEXT', label: 'Multiline Textarea', icon: '📄' },
  { type: 'NUMBER', label: 'Numeric Value', icon: '🔢' },
  { type: 'DATE', label: 'Date Selector', icon: '📅' },
  { type: 'EMAIL', label: 'Email Address', icon: '✉️' },
  { type: 'PHONE', label: 'Phone Number', icon: '📞' },
  { type: 'DROPDOWN', label: 'Dropdown List', icon: '🔽' },
  { type: 'RADIO', label: 'Radio Button Group', icon: '🔘' },
  { type: 'CHECKBOX', label: 'Checkbox / Toggle', icon: '☑️' },
  { type: 'MULTI_SELECT', label: 'Multi-Select Tags', icon: '🏷️' },
  { type: 'ATTACHMENT', label: 'File Attachment (PDF/Img)', icon: '📎' },
  { type: 'EMPLOYEE', label: 'Employee Lookup', icon: '👤' },
  { type: 'REFERENCE', label: 'Taxonomy Reference', icon: '🏛️' },
]

export function FormBuilder() {
  const [schema, setSchema] = useState<FormSchemaState>(DEFAULT_EMPLOYEE_SCHEMA)
  const [selectedSectionId, setSelectedSectionId] = useState<string>('sec-personal')
  const [selectedFieldId, setSelectedFieldId] = useState<string>('f-4')
  const [previewMode, setPreviewMode] = useState<'designer' | 'desktop' | 'mobile'>('designer')
  const [saveBanner, setSaveBanner] = useState<string | null>(null)

  const activeSection = schema.sections.find((s) => s.id === selectedSectionId) ?? schema.sections[0]
  const activeField = schema.sections
    .flatMap((s) => s.fields)
    .find((f) => f.id === selectedFieldId)

  // ---------------------------------------------------------------------------
  // Section Management
  // ---------------------------------------------------------------------------
  const addSection = () => {
    const newId = `sec-${Date.now()}`
    const newSection: FormSectionItem = {
      id: newId,
      key: `section_${schema.sections.length + 1}`,
      label: `New Section ${schema.sections.length + 1}`,
      fields: [],
    }
    setSchema((prev) => ({
      ...prev,
      sections: [...prev.sections, newSection],
    }))
    setSelectedSectionId(newId)
  }

  const removeSection = (sectionId: string) => {
    if (schema.sections.length <= 1) {
      alert('A form must have at least one section.')
      return
    }
    setSchema((prev) => ({
      ...prev,
      sections: prev.sections.filter((s) => s.id !== sectionId),
    }))
    if (selectedSectionId === sectionId) {
      const remaining = schema.sections.filter((s) => s.id !== sectionId)
      if (remaining[0]) {
        setSelectedSectionId(remaining[0].id)
      }
    }
  }

  const updateSectionLabel = (sectionId: string, label: string) => {
    setSchema((prev) => ({
      ...prev,
      sections: prev.sections.map((s) =>
        s.id === sectionId
          ? { ...s, label, key: label.toLowerCase().replace(/[^a-z0-9]+/g, '_') }
          : s,
      ),
    }))
  }

  // ---------------------------------------------------------------------------
  // Field Management
  // ---------------------------------------------------------------------------
  const addFieldToActiveSection = (type: FieldType, label: string) => {
    if (!activeSection) return
    const newFieldId = `f-${Date.now()}`
    const key = label.toLowerCase().replace(/[^a-z0-9]+/g, '_')
    const newField: FormFieldItem = {
      id: newFieldId,
      key,
      label,
      type,
      required: false,
      editable: true,
      custom: true,
      options:
        type === 'DROPDOWN' || type === 'RADIO' || type === 'MULTI_SELECT'
          ? [
              { value: 'opt_1', label: 'Option 1' },
              { value: 'opt_2', label: 'Option 2' },
            ]
          : undefined,
    }

    setSchema((prev) => ({
      ...prev,
      sections: prev.sections.map((sec) =>
        sec.id === activeSection.id
          ? { ...sec, fields: [...sec.fields, newField] }
          : sec,
      ),
    }))
    setSelectedFieldId(newFieldId)
  }

  const removeField = (fieldId: string) => {
    setSchema((prev) => ({
      ...prev,
      sections: prev.sections.map((sec) => ({
        ...sec,
        fields: sec.fields.filter((f) => f.id !== fieldId),
      })),
    }))
    if (selectedFieldId === fieldId) {
      setSelectedFieldId('')
    }
  }

  const moveField = (sectionId: string, fieldIndex: number, direction: 'up' | 'down') => {
    setSchema((prev) => ({
      ...prev,
      sections: prev.sections.map((sec) => {
        if (sec.id !== sectionId) return sec
        const newFields = [...sec.fields]
        const targetIndex = direction === 'up' ? fieldIndex - 1 : fieldIndex + 1
        if (targetIndex < 0 || targetIndex >= newFields.length) return sec
        const currentItem = newFields[fieldIndex]
        const targetItem = newFields[targetIndex]
        if (!currentItem || !targetItem) return sec
        newFields[fieldIndex] = targetItem
        newFields[targetIndex] = currentItem
        return { ...sec, fields: newFields }
      }),
    }))
  }

  const updateActiveField = (patch: Partial<FormFieldItem>) => {
    if (!activeField) return
    setSchema((prev) => ({
      ...prev,
      sections: prev.sections.map((sec) => ({
        ...sec,
        fields: sec.fields.map((f) => (f.id === activeField.id ? { ...f, ...patch } : f)),
      })),
    }))
  }

  const addOptionToField = () => {
    if (!activeField) return
    const currentOptions = activeField.options ?? []
    const nextIdx = currentOptions.length + 1
    const newOptions = [...currentOptions, { value: `opt_${nextIdx}`, label: `Option ${nextIdx}` }]
    updateActiveField({ options: newOptions })
  }

  const removeOptionFromField = (optIdx: number) => {
    if (!activeField || !activeField.options) return
    const newOptions = activeField.options.filter((_, idx) => idx !== optIdx)
    updateActiveField({ options: newOptions })
  }

  const updateOption = (optIdx: number, key: 'value' | 'label', val: string) => {
    if (!activeField || !activeField.options) return
    const newOptions = activeField.options.map((opt, idx) =>
      idx === optIdx ? { ...opt, [key]: val } : opt,
    )
    updateActiveField({ options: newOptions })
  }

  // ---------------------------------------------------------------------------
  // Publish & Export
  // ---------------------------------------------------------------------------
  const handlePublish = () => {
    const nextVersionParts = schema.version.split('.').map(Number)
    const minor = (nextVersionParts[1] ?? 0) + 1
    const newVer = `${nextVersionParts[0] ?? 1}.${minor}.${nextVersionParts[2] ?? 0}`
    setSchema((prev) => ({ ...prev, version: newVer }))
    setSaveBanner(`Form Schema published successfully as v${newVer}! Live on Android, iOS & Web.`)
    setTimeout(() => setSaveBanner(null), 4000)
  }

  const handleExportJson = () => {
    const dataStr = 'data:text/json;charset=utf-8,' + encodeURIComponent(JSON.stringify(schema, null, 2))
    const downloadAnchor = document.createElement('a')
    downloadAnchor.setAttribute('href', dataStr)
    downloadAnchor.setAttribute('download', `form_schema_${schema.entityType}_v${schema.version}.json`)
    document.body.appendChild(downloadAnchor)
    downloadAnchor.click()
    downloadAnchor.remove()
  }

  return (
    <div className="builder-shell">
      {/* Header Bar */}
      <div className="builder-header">
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
            <h1 className="text-lg" style={{ margin: 0, fontWeight: 700 }}>
              Dynamic Form & Custom Field Designer
            </h1>
            <Badge tone="success">v{schema.version}</Badge>
            <Badge tone="neutral">Active Tenant: demo</Badge>
          </div>
          <p style={{ margin: 0, fontSize: '0.875rem', color: 'var(--color-on-surface-muted)' }}>
            Design and publish custom fields across Android, iOS, and Web without app updates or database migrations.
          </p>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
          <div style={{ display: 'flex', border: '1px solid var(--color-outline)', borderRadius: 'var(--radius-control)', overflow: 'hidden' }}>
            <button
              className="btn btn--ghost"
              style={{
                borderRadius: 0,
                background: previewMode === 'designer' ? 'var(--color-brand-primary)' : 'transparent',
                color: previewMode === 'designer' ? '#fff' : 'inherit',
                padding: 'var(--space-1) var(--space-3)',
                fontSize: '0.8125rem',
              }}
              onClick={() => setPreviewMode('designer')}
            >
              🎨 Designer
            </button>
            <button
              className="btn btn--ghost"
              style={{
                borderRadius: 0,
                background: previewMode === 'desktop' ? 'var(--color-brand-primary)' : 'transparent',
                color: previewMode === 'desktop' ? '#fff' : 'inherit',
                padding: 'var(--space-1) var(--space-3)',
                fontSize: '0.8125rem',
              }}
              onClick={() => setPreviewMode('desktop')}
            >
              💻 Web Preview
            </button>
            <button
              className="btn btn--ghost"
              style={{
                borderRadius: 0,
                background: previewMode === 'mobile' ? 'var(--color-brand-primary)' : 'transparent',
                color: previewMode === 'mobile' ? '#fff' : 'inherit',
                padding: 'var(--space-1) var(--space-3)',
                fontSize: '0.8125rem',
              }}
              onClick={() => setPreviewMode('mobile')}
            >
              📱 Mobile Preview
            </button>
          </div>

          <Button variant="secondary" onClick={handleExportJson}>
            Export JSON
          </Button>
          <Button variant="primary" onClick={handlePublish}>
            Publish Schema
          </Button>
        </div>
      </div>

      {saveBanner && (
        <div style={{ background: '#dcfce7', color: '#15803d', padding: 'var(--space-3)', borderRadius: 'var(--radius-control)', fontWeight: 500 }}>
          {saveBanner}
        </div>
      )}

      {/* Target Entity Selector */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)', background: 'var(--color-surface-raised)', padding: 'var(--space-3)', borderRadius: 'var(--radius-control)', border: '1px solid var(--color-outline-variant)' }}>
        <span style={{ fontSize: '0.875rem', fontWeight: 600 }}>Form Target:</span>
        {['employee', 'department', 'location', 'designation', 'company'].map((ent) => (
          <button
            key={ent}
            className="btn btn--ghost"
            style={{
              padding: '4px 12px',
              fontSize: '0.8125rem',
              borderRadius: 'var(--radius-pill)',
              background: schema.entityType === ent ? 'var(--color-brand-primary-container)' : 'transparent',
              color: schema.entityType === ent ? 'var(--color-brand-on-primary-container)' : 'inherit',
              fontWeight: schema.entityType === ent ? 600 : 400,
            }}
            onClick={() => setSchema((prev) => ({ ...prev, entityType: ent }))}
          >
            {ent.toUpperCase()}
          </button>
        ))}
      </div>

      {/* Main Workspace Mode */}
      {previewMode === 'designer' ? (
        <div className="builder-grid">
          {/* Left Column: Field Palette */}
          <div className="palette-card">
            <h3 style={{ margin: 0, fontSize: '0.9375rem', fontWeight: 600 }}>Field Library</h3>
            <p style={{ margin: 0, fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>
              Click any element to append it to the selected section.
            </p>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
              {FIELD_PALETTE.map((item) => (
                <div
                  key={item.type}
                  className="palette-item"
                  onClick={() => addFieldToActiveSection(item.type, item.label)}
                >
                  <span className="palette-item__icon">{item.icon}</span>
                  <span>{item.label}</span>
                </div>
              ))}
            </div>
          </div>

          {/* Center Column: Form Sections Canvas */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ fontSize: '0.875rem', fontWeight: 600, color: 'var(--color-on-surface-muted)' }}>
                SECTIONS & LAYOUT ({schema.sections.length})
              </span>
              <Button variant="secondary" onClick={addSection}>
                + Add New Section
              </Button>
            </div>

            {schema.sections.map((section) => {
              const isSelectedSection = section.id === selectedSectionId
              return (
                <div
                  key={section.id}
                  className={`canvas-section ${isSelectedSection ? 'canvas-section--active' : ''}`}
                  onClick={() => setSelectedSectionId(section.id)}
                >
                  <div className="canvas-section__header">
                    <input
                      style={{
                        fontSize: '1rem',
                        fontWeight: 600,
                        border: 'none',
                        background: 'transparent',
                        color: 'inherit',
                        outline: 'none',
                        width: '70%',
                      }}
                      value={section.label}
                      onChange={(e) => updateSectionLabel(section.id, e.target.value)}
                    />
                    <div style={{ display: 'flex', gap: 'var(--space-1)' }}>
                      <span style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)', alignSelf: 'center', marginRight: 'var(--space-2)' }}>
                        {section.fields.length} fields
                      </span>
                      <Button
                        variant="ghost"
                        style={{ padding: '2px 8px', fontSize: '0.75rem', color: 'var(--color-danger)' }}
                        onClick={(e) => {
                          e.stopPropagation()
                          removeSection(section.id)
                        }}
                      >
                        Delete Section
                      </Button>
                    </div>
                  </div>

                  <div className="canvas-field-list">
                    {section.fields.length === 0 ? (
                      <div style={{ textAlign: 'center', padding: 'var(--space-4)', color: 'var(--color-on-surface-muted)', fontSize: '0.8125rem' }}>
                        No fields in this section yet. Click a field from the library to add one.
                      </div>
                    ) : (
                      section.fields.map((field, idx) => {
                        const isSelectedField = field.id === selectedFieldId
                        return (
                          <div
                            key={field.id}
                            className={`canvas-field-card ${isSelectedField ? 'canvas-field-card--selected' : ''}`}
                            onClick={(e) => {
                              e.stopPropagation()
                              setSelectedSectionId(section.id)
                              setSelectedFieldId(field.id)
                            }}
                          >
                            <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
                              <span style={{ cursor: 'grab', opacity: 0.5 }}>⠿</span>
                              <div>
                                <div style={{ fontSize: '0.875rem', fontWeight: 500 }}>
                                  {field.label} {field.required && <span style={{ color: 'var(--color-danger)' }}>*</span>}
                                </div>
                                <div style={{ fontSize: '0.75rem', opacity: 0.75, fontFamily: 'var(--font-mono)' }}>
                                  {field.key} &bull; {field.type}
                                </div>
                              </div>
                            </div>

                            <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-1)' }} onClick={(e) => e.stopPropagation()}>
                              {field.custom ? <Badge tone="warning">Custom</Badge> : <Badge tone="neutral">Core</Badge>}
                              <button
                                className="btn btn--ghost"
                                style={{ padding: '2px 6px', fontSize: '0.75rem' }}
                                disabled={idx === 0}
                                onClick={() => moveField(section.id, idx, 'up')}
                              >
                                ▲
                              </button>
                              <button
                                className="btn btn--ghost"
                                style={{ padding: '2px 6px', fontSize: '0.75rem' }}
                                disabled={idx === section.fields.length - 1}
                                onClick={() => moveField(section.id, idx, 'down')}
                              >
                                ▼
                              </button>
                              <button
                                className="btn btn--ghost"
                                style={{ padding: '2px 6px', fontSize: '0.75rem', color: 'var(--color-danger)' }}
                                onClick={() => removeField(field.id)}
                              >
                                ✕
                              </button>
                            </div>
                          </div>
                        )
                      })
                    )}
                  </div>
                </div>
              )
            })}
          </div>

          {/* Right Column: Field Property Inspector */}
          <div className="palette-card builder-inspector-col">
            <h3 style={{ margin: 0, fontSize: '0.9375rem', fontWeight: 600 }}>Field Inspector</h3>
            {activeField ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
                <div>
                  <label style={{ fontSize: '0.75rem', fontWeight: 600, display: 'block', marginBottom: '4px' }}>
                    DISPLAY LABEL
                  </label>
                  <input
                    className="field__input"
                    value={activeField.label}
                    onChange={(e) => updateActiveField({ label: e.target.value })}
                  />
                </div>

                <div>
                  <label style={{ fontSize: '0.75rem', fontWeight: 600, display: 'block', marginBottom: '4px' }}>
                    API KEY (COLUMN / JSON PROPERTY)
                  </label>
                  <input
                    className="field__input"
                    style={{ fontFamily: 'var(--font-mono)', fontSize: '0.8125rem' }}
                    value={activeField.key}
                    onChange={(e) => updateActiveField({ key: e.target.value.toLowerCase().replace(/[^a-z0-9_]/g, '') })}
                  />
                </div>

                <div>
                  <label style={{ fontSize: '0.75rem', fontWeight: 600, display: 'block', marginBottom: '4px' }}>
                    HELP / TOOLTIP TEXT
                  </label>
                  <input
                    className="field__input"
                    value={activeField.helpText ?? ''}
                    placeholder="Guidance shown to employee"
                    onChange={(e) => updateActiveField({ helpText: e.target.value })}
                  />
                </div>

                <div style={{ display: 'flex', gap: 'var(--space-4)', padding: 'var(--space-2) 0' }}>
                  <label style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-1)', fontSize: '0.8125rem', cursor: 'pointer' }}>
                    <input
                      type="checkbox"
                      checked={activeField.required}
                      onChange={(e) => updateActiveField({ required: e.target.checked })}
                    />
                    Required Field
                  </label>

                  <label style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-1)', fontSize: '0.8125rem', cursor: 'pointer' }}>
                    <input
                      type="checkbox"
                      checked={activeField.editable}
                      onChange={(e) => updateActiveField({ editable: e.target.checked })}
                    />
                    Editable by Employee
                  </label>
                </div>

                {/* Choices manager for Dropdown / Radio / MultiSelect */}
                {(activeField.type === 'DROPDOWN' || activeField.type === 'RADIO' || activeField.type === 'MULTI_SELECT') && (
                  <div style={{ borderTop: '1px solid var(--color-outline-variant)', paddingTop: 'var(--space-3)' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-2)' }}>
                      <label style={{ fontSize: '0.75rem', fontWeight: 600 }}>OPTIONS / CHOICES</label>
                      <button className="btn btn--ghost" style={{ fontSize: '0.75rem', padding: '2px 6px' }} onClick={addOptionToField}>
                        + Add Option
                      </button>
                    </div>

                    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-1)' }}>
                      {activeField.options?.map((opt, optIdx) => (
                        <div key={optIdx} style={{ display: 'flex', gap: 'var(--space-1)', alignItems: 'center' }}>
                          <input
                            className="field__input"
                            style={{ padding: '2px 6px', fontSize: '0.75rem', flex: 1 }}
                            value={opt.label}
                            placeholder="Display label"
                            onChange={(e) => updateOption(optIdx, 'label', e.target.value)}
                          />
                          <input
                            className="field__input"
                            style={{ padding: '2px 6px', fontSize: '0.75rem', width: '70px', fontFamily: 'var(--font-mono)' }}
                            value={opt.value}
                            placeholder="code"
                            onChange={(e) => updateOption(optIdx, 'value', e.target.value)}
                          />
                          <button
                            className="btn btn--ghost"
                            style={{ padding: '2px 4px', fontSize: '0.75rem', color: 'var(--color-danger)' }}
                            onClick={() => removeOptionFromField(optIdx)}
                          >
                            ✕
                          </button>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* Validation Rules */}
                <div style={{ borderTop: '1px solid var(--color-outline-variant)', paddingTop: 'var(--space-3)' }}>
                  <label style={{ fontSize: '0.75rem', fontWeight: 600, display: 'block', marginBottom: 'var(--space-2)' }}>
                    VALIDATION RULES (REGEX & LIMITS)
                  </label>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
                    <input
                      className="field__input"
                      placeholder="Regex Pattern (e.g. ^[0-9]{9}[vVxX]$)"
                      style={{ fontSize: '0.75rem', fontFamily: 'var(--font-mono)' }}
                      value={activeField.validation?.pattern ?? ''}
                      onChange={(e) =>
                        updateActiveField({
                          validation: { ...activeField.validation, pattern: e.target.value },
                        })
                      }
                    />
                    <input
                      className="field__input"
                      placeholder="Pattern Error Message"
                      style={{ fontSize: '0.75rem' }}
                      value={activeField.validation?.patternMessage ?? ''}
                      onChange={(e) =>
                        updateActiveField({
                          validation: { ...activeField.validation, patternMessage: e.target.value },
                        })
                      }
                    />
                  </div>
                </div>
              </div>
            ) : (
              <div style={{ color: 'var(--color-on-surface-muted)', fontSize: '0.8125rem' }}>
                Select a field on the canvas to configure its properties.
              </div>
            )}
          </div>
        </div>
      ) : previewMode === 'desktop' ? (
        /* Desktop Interactive Preview */
        <Card title={`Interactive Web Portal Preview: ${schema.entityType.toUpperCase()}`}>
          <div style={{ maxWidth: '780px', margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 'var(--space-6)' }}>
            {schema.sections.map((sec) => (
              <div key={sec.id} style={{ borderBottom: '1px solid var(--color-outline-variant)', paddingBottom: 'var(--space-4)' }}>
                <h3 style={{ fontSize: '1.125rem', fontWeight: 600, marginBottom: 'var(--space-3)', color: 'var(--color-brand-primary)' }}>
                  {sec.label}
                </h3>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 'var(--space-3)' }}>
                  {sec.fields.map((f) => (
                    <div key={f.id} style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                      <label style={{ fontSize: '0.8125rem', fontWeight: 500 }}>
                        {f.label} {f.required && <span style={{ color: 'var(--color-danger)' }}>*</span>}
                      </label>
                      {f.type === 'DROPDOWN' ? (
                        <select className="select" defaultValue="">
                          <option value="" disabled>Select an option...</option>
                          {f.options?.map((o) => (
                            <option key={o.value} value={o.value}>{o.label}</option>
                          ))}
                        </select>
                      ) : f.type === 'RADIO' ? (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                          {f.options?.map((o) => (
                            <label key={o.value} style={{ fontSize: '0.8125rem', display: 'flex', alignItems: 'center', gap: '6px' }}>
                              <input type="radio" name={f.key} value={o.value} />
                              {o.label}
                            </label>
                          ))}
                        </div>
                      ) : f.type === 'CHECKBOX' ? (
                        <label style={{ fontSize: '0.8125rem', display: 'flex', alignItems: 'center', gap: '6px' }}>
                          <input type="checkbox" />
                          Yes / Confirmed
                        </label>
                      ) : f.type === 'ATTACHMENT' ? (
                        <input type="file" className="field__input" style={{ padding: '4px' }} />
                      ) : f.type === 'MULTILINE_TEXT' ? (
                        <textarea className="field__input" rows={3} placeholder={f.helpText} />
                      ) : (
                        <input
                          type={f.type === 'NUMBER' ? 'number' : f.type === 'DATE' ? 'date' : f.type === 'EMAIL' ? 'email' : 'text'}
                          className="field__input"
                          placeholder={f.helpText ?? `Enter ${f.label.toLowerCase()}`}
                          disabled={!f.editable}
                        />
                      )}
                      {f.helpText && <span style={{ fontSize: '0.75rem', color: 'var(--color-on-surface-muted)' }}>{f.helpText}</span>}
                    </div>
                  ))}
                </div>
              </div>
            ))}
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 'var(--space-2)' }}>
              <Button variant="secondary" onClick={() => alert('Simulated form cancelled')}>Cancel</Button>
              <Button variant="primary" onClick={() => alert('Validation passed! Simulated payload accepted.')}>Save Changes</Button>
            </div>
          </div>
        </Card>
      ) : (
        /* Mobile Simulator (Android / iOS Preview) */
        <div className="simulator-shell">
          <div className="phone-frame">
            <div className="phone-notch">
              <div className="phone-notch-bar" />
            </div>

            <div className="phone-screen">
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', paddingBottom: 'var(--space-2)', borderBottom: '1px solid var(--color-outline-variant)' }}>
                <span style={{ fontSize: '0.875rem', fontWeight: 700 }}>HR Mobile</span>
                <span style={{ fontSize: '0.75rem', color: 'var(--color-success)', fontWeight: 600 }}>Sync: Live</span>
              </div>

              <div style={{ fontSize: '1rem', fontWeight: 700 }}>Edit Profile</div>

              {schema.sections.map((sec) => (
                <div key={sec.id} style={{ background: 'var(--color-surface-raised)', padding: 'var(--space-3)', borderRadius: 'var(--radius-control)', border: '1px solid var(--color-outline-variant)' }}>
                  <div style={{ fontSize: '0.8125rem', fontWeight: 700, color: 'var(--color-brand-primary)', marginBottom: 'var(--space-2)' }}>
                    {sec.label}
                  </div>

                  <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
                    {sec.fields.map((f) => (
                      <div key={f.id}>
                        <div style={{ fontSize: '0.75rem', fontWeight: 600, marginBottom: '2px' }}>
                          {f.label} {f.required && <span style={{ color: 'var(--color-danger)' }}>*</span>}
                        </div>
                        {f.type === 'DROPDOWN' ? (
                          <select className="select" style={{ width: '100%', fontSize: '0.75rem', minHeight: '32px' }}>
                            {f.options?.map((o) => (
                              <option key={o.value} value={o.value}>{o.label}</option>
                            ))}
                          </select>
                        ) : (
                          <input
                            type={f.type === 'DATE' ? 'date' : 'text'}
                            className="field__input"
                            style={{ fontSize: '0.75rem', padding: '4px 8px' }}
                            placeholder={f.helpText ?? f.label}
                            disabled={!f.editable}
                          />
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              ))}

              <Button variant="primary" style={{ marginTop: 'auto', width: '100%' }}>
                Submit to HR
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
