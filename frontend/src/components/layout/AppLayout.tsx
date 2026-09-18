import { Outlet } from 'react-router-dom'
import { Sidebar } from './Sidebar'
import { Header } from './Header'
import { SidebarProvider } from '../../context/SidebarContext'
import { IdentitySetupBanner } from '../common/IdentitySetupBanner'

export function AppLayout() {
  return (
    <SidebarProvider>
      <div className="app-shell">
        <Sidebar />
        <div className="app-main">
          <Header />
          <main className="app-content">
            <IdentitySetupBanner />
            <Outlet />
          </main>
        </div>
      </div>
    </SidebarProvider>
  )
}
