import type { SatelliteGroupKey } from '../../api/groups/types'
import '../../styles/panels/groups-panel.css'

type SatelliteSearchScope = 'enabled' | 'all' | SatelliteGroupKey

type SearchResultItem = {
  entityId: string
  groupKey: SatelliteGroupKey
  groupLabel: string
  satelliteId: number
  satelliteName: string
  objectId: string
  noradCatId: number
  hasLivePosition: boolean
}

type ScopeOption = {
  key: SatelliteGroupKey
  label: string
}

type GroupRow = {
  key: SatelliteGroupKey
  label: string
  color: string
  count: number
  loading: boolean
  error: string
  checked: boolean
}

type GroupsPanelProps = {
  allSelected: boolean
  selectedPreset: string
  searchScope: SatelliteSearchScope
  searchQuery: string
  searchResultItems: SearchResultItem[]
  searchScopeOptions: ScopeOption[]
  groupRows: GroupRow[]
  onToggleAll: () => void
  onPresetChange: (preset: string) => void
  onSearchScopeChange: (scope: SatelliteSearchScope) => void
  onSearchQueryChange: (query: string) => void
  onSearchResultSelect: (item: SearchResultItem) => void
  onToggleGroup: (groupKey: SatelliteGroupKey) => void
}

export function GroupsPanel({
  allSelected,
  selectedPreset,
  searchScope,
  searchQuery,
  searchResultItems,
  searchScopeOptions,
  groupRows,
  onToggleAll,
  onPresetChange,
  onSearchScopeChange,
  onSearchQueryChange,
  onSearchResultSelect,
  onToggleGroup,
}: GroupsPanelProps) {
  return (
    <section className="collapsible side-drawer" aria-label="Gruppi satelliti">
      <h3>Gruppi satelliti</h3>
      <label className="select-all">
        <input type="checkbox" checked={allSelected} onChange={onToggleAll} />
        <span>Seleziona tutti i gruppi</span>
      </label>

      <div className="group-preset-row">
        <label htmlFor="group-preset">Preset gruppi</label>
        <select
          id="group-preset"
          value={selectedPreset}
          onChange={(event) => onPresetChange(event.target.value)}
        >
          <option value="custom">Personalizzato</option>
          <option value="stations">Solo stazioni</option>
          <option value="navigation">Navigazione GNSS</option>
          <option value="leo">LEO tracking</option>
          <option value="all">Tutti i gruppi</option>
        </select>
      </div>

      <div className="search-panel">
        <div className="group-preset-row">
          <label htmlFor="satellite-search-scope">Ambito ricerca</label>
          <select
            id="satellite-search-scope"
            value={searchScope}
            onChange={(event) => onSearchScopeChange(event.target.value as SatelliteSearchScope)}
          >
            <option value="enabled">Gruppi attivi</option>
            <option value="all">Tutti i gruppi</option>
            {searchScopeOptions.map((group) => (
              <option key={group.key} value={group.key}>
                {group.label}
              </option>
            ))}
          </select>
        </div>

        <label htmlFor="satellite-search-input" className="search-label">
          Cerca satellite
        </label>
        <input
          id="satellite-search-input"
          className="search-input"
          type="text"
          value={searchQuery}
          onChange={(event) => onSearchQueryChange(event.target.value)}
          placeholder="Nome, NORAD o object id"
        />

        <div className="search-results-meta">{searchResultItems.length} risultati</div>
        <div className="search-results-list">
          {searchResultItems.map((item) => (
            <button
              key={item.entityId}
              type="button"
              className="search-result-item"
              onClick={() => onSearchResultSelect(item)}
            >
              <span>{item.satelliteName}</span>
              <small>
                {item.groupLabel} | NORAD {item.noradCatId}
                {!item.hasLivePosition ? ' | in attesa posizione live' : ''}
              </small>
            </button>
          ))}
        </div>
      </div>

      <div className="group-list">
        {groupRows.map((group) => (
          <label
            key={group.key}
            className={`group-item ${group.loading ? 'is-loading' : ''}`}
            aria-busy={group.loading ? 'true' : 'false'}
          >
            <input
              type="checkbox"
              checked={group.checked}
              onChange={() => onToggleGroup(group.key)}
            />
            <span
              className="group-color"
              style={{
                backgroundColor: group.color,
                boxShadow: `0 0 8px ${group.color}, 0 0 0 1px rgba(4, 10, 24, 0.85)`,
              }}
              aria-hidden="true"
            />
            <span className="group-name">{group.label}</span>
            <span className="group-meta">{`${group.count} sat`}</span>
            {group.error ? <span className="group-error">!</span> : null}
          </label>
        ))}
      </div>
    </section>
  )
}
