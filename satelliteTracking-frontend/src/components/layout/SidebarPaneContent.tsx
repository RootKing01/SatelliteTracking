import type { ReactNode } from 'react'
import type { SidebarPane } from './PanelSidebarButtons'

type SidebarPaneContentProps = {
  pane: SidebarPane
  children: ReactNode
}

export function SidebarPaneContent({ pane, children }: SidebarPaneContentProps) {
  return <div id={`panel-${pane}`}>{children}</div>
}
