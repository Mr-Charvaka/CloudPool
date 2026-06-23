import { useState } from 'react';

export default function ProvisionerPage() {
  const [mode, setMode] = useState<'visual' | 'json'>('visual');

  return (
    <div className="page active" id="page-provisioner">
      <div>
        <div className="page-title">DATABASE PROVISIONER</div>
        <div className="page-sub">Provision dynamic tables, design schemas with types, and perform CRUD operations.</div>
      </div>
      <div className="panels" style={{ flex: '0 0 auto', minHeight: '380px' }}>
        <div className="panel panel-left">
          <div className="panel-header">
            <div className="panel-title">PROVISION NEW TABLE</div>
            <div className="panel-badge">RDS_PROVISIONER</div>
          </div>
          <div className="panel-body">
            <div style={{ display: 'flex', borderBottom: '1px solid var(--border2)', marginBottom: '12px', gap: '8px', paddingBottom: '6px' }}>
              <span className={`chip ${mode === 'visual' ? 'active' : ''}`} style={{ cursor: 'pointer' }} onClick={() => setMode('visual')}>VISUAL BUILDER</span>
              <span className={`chip ${mode === 'json' ? 'active' : ''}`} style={{ cursor: 'pointer' }} onClick={() => setMode('json')}>JSON SCHEMA EDITOR</span>
            </div>

            {mode === 'visual' && (
              <div id="visualProvForm" style={{ display: 'flex', flexDirection: 'column', gap: '11px', flex: 1 }}>
                <div className="field">
                  <div className="field-label">TABLE NAME (PHYSICAL NAME)</div>
                  <input type="text" id="provTableName" placeholder="customer_leads" />
                </div>
                <div className="field">
                  <div className="field-label">DISPLAY NAME</div>
                  <input type="text" id="provTableDisplay" placeholder="Customer Leads" />
                </div>
                <div className="field">
                  <div className="field-label">DESCRIPTION</div>
                  <input type="text" id="provTableDesc" placeholder="Table for storing incoming sales leads" />
                </div>
                
                <div style={{ borderTop: '1px solid var(--border2)', paddingTop: '10px', marginTop: '5px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                    <div className="field-label" style={{ marginBottom: 0 }}>SCHEMA FIELDS (id is automatically added)</div>
                    <button type="button" className="btn btn-outline" style={{ padding: '2px 8px', fontSize: '8px' }}>+ ADD FIELD</button>
                  </div>
                  <div id="schemaFieldsContainer" style={{ display: 'flex', flexDirection: 'column', gap: '6px', maxHeight: '120px', overflowY: 'auto', paddingRight: '4px' }}>
                    {/* Dynamic field rows go here */}
                  </div>
                </div>
              </div>
            )}

            {mode === 'json' && (
              <div id="jsonProvForm" style={{ display: 'flex', flexDirection: 'column', gap: '8px', flex: 1 }}>
                <div className="field-label">JSON SCHEMA DEFINITION (RAW CODE)</div>
                <textarea className="code-editor" id="jsonSchemaEditor" style={{ height: '240px', fontFamily: 'var(--font)', fontSize: '9px', lineHeight: 1.4 }} defaultValue={`{
  "name": "customer_leads",
  "displayName": "Customer Leads",
  "description": "Table for storing incoming sales leads",
  "fields": [
    {
      "fieldName": "email",
      "fieldType": "VARCHAR",
      "isRequired": true
    }
  ]
}`} />
              </div>
            )}

            <div style={{ marginTop: 'auto', paddingTop: '10px' }}>
              <button className="btn" style={{ width: '100%', justifyContent: 'center' }}>⚡ PROVISION TABLE</button>
            </div>
          </div>
        </div>

        <div className="panel panel-right">
          <div className="panel-header">
            <div className="panel-title">ACTIVE PROVISIONED TABLES</div>
            <div className="panel-badge" id="provTablesCount">0 TABLES</div>
          </div>
          <div className="panel-body" style={{ gap: '8px', overflowY: 'auto' }} id="activeTablesList">
            {/* Active table list elements */}
          </div>
        </div>
      </div>
    </div>
  );
}
