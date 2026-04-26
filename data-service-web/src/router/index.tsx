import { Navigate, Route, Routes } from 'react-router-dom'
import AuditsPage from '../pages/AuditsPage'
import ConnectionsPage from '../pages/ConnectionsPage'
import ServiceEditPage from '../pages/ServiceEditPage'
import ServicesPage from '../pages/ServicesPage'

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/connections" element={<ConnectionsPage />} />
      <Route path="/services" element={<ServicesPage />} />
      <Route path="/services/new" element={<ServiceEditPage />} />
      <Route path="/services/:id/edit" element={<ServiceEditPage />} />
      <Route path="/audits" element={<AuditsPage />} />
      <Route path="*" element={<Navigate to="/connections" replace />} />
    </Routes>
  )
}
