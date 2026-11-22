import { BrowserRouter, Route, Routes } from "react-router-dom";
import { AdminPage } from "./admin/AdminPage";
import { LoginPage } from "./auth/LoginPage";
import { SessionProvider } from "./auth/SessionContext";
import { ReportPage } from "./report/ReportPage";
import { RoutePage } from "./route/RoutePage";
import { HomeRedirect, RequireAuth, RequireScreen, Shell } from "./shell/Shell";
import { ShipmentPage } from "./shipment/ShipmentPage";
import { WarehousePage } from "./warehouse/WarehousePage";

export function App() {
  return (
    <SessionProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route element={<RequireAuth />}>
            <Route element={<Shell />}>
              <Route index element={<HomeRedirect />} />
              <Route element={<RequireScreen path="/warehouse" />}>
                <Route path="warehouse" element={<WarehousePage />} />
              </Route>
              <Route element={<RequireScreen path="/shipments" />}>
                <Route path="shipments" element={<ShipmentPage />} />
              </Route>
              <Route element={<RequireScreen path="/routes" />}>
                <Route path="routes" element={<RoutePage />} />
              </Route>
              <Route element={<RequireScreen path="/reports" />}>
                <Route path="reports" element={<ReportPage />} />
              </Route>
              <Route element={<RequireScreen path="/admin" />}>
                <Route path="admin" element={<AdminPage />} />
              </Route>
            </Route>
          </Route>
        </Routes>
      </BrowserRouter>
    </SessionProvider>
  );
}
