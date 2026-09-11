import { Routes, Route, Navigate, useLocation } from "react-router";
import type { ReactNode } from "react";
import Layout from "@/components/Layout";
import Dashboard from "@/pages/Dashboard";
import Transactions from "@/pages/Transactions";
import Buyers from "@/pages/Buyers";
import Reports from "@/pages/Reports";
import BulkUpload from "@/pages/BulkUpload";
import Login from "@/pages/Login";
import About from "@/pages/About";
import Settings from "@/pages/Settings";
import Bills from "@/pages/Bills";
import Items from "@/pages/Items";
import Categories from "@/pages/Categories";
import Transports from "@/pages/Transports";
import NotFound from "@/pages/NotFound";
import { useAuth } from "@/hooks/useAuth";

function RequireAuth({ children }: { children: ReactNode }) {
  const location = useLocation();
  const { user, isLoading } = useAuth();

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-[#f5f0e8] text-[#1e2a4a]">
        Checking authentication...
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  return <>{children}</>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route
        path="/"
        element={
          <RequireAuth>
            <Layout>
              <Dashboard />
            </Layout>
          </RequireAuth>
        }
      />
      <Route
        path="/bills"
        element={
          <RequireAuth>
            <Layout>
              <Bills />
            </Layout>
          </RequireAuth>
        }
      />
      <Route
        path="/items"
        element={
          <RequireAuth>
            <Layout>
              <Items />
            </Layout>
          </RequireAuth>
        }
      />
         <Route
        path="/categories"
        element={
          <RequireAuth>
            <Layout>
              <Categories />
            </Layout>
          </RequireAuth>
        }
      />
      <Route
        path="/transports"
        element={
          <RequireAuth>
            <Layout>
              <Transports />
            </Layout>
          </RequireAuth>
        }
      />
      <Route
        path="/transactions"
        element={
          <RequireAuth>
            <Layout>
              <Transactions />
            </Layout>
          </RequireAuth>
        }
      />
      <Route
        path="/buyers"
        element={
          <RequireAuth>
            <Layout>
              <Buyers />
            </Layout>
          </RequireAuth>
        }
      />
      <Route
        path="/reports"
        element={
          <RequireAuth>
            <Layout>
              <Reports />
            </Layout>
          </RequireAuth>
        }
      />
      <Route
        path="/bulk-upload"
        element={
          <RequireAuth>
            <Layout>
              <BulkUpload />
            </Layout>
          </RequireAuth>
        }
      />
      <Route
        path="/about"
        element={
          <RequireAuth>
            <Layout>
              <About />
            </Layout>
          </RequireAuth>
        }
      />
      <Route
        path="/settings"
        element={
          <RequireAuth>
            <Layout>
              <Settings />
            </Layout>
          </RequireAuth>
        }
      />
      <Route
        path="/setting"
        element={
          <RequireAuth>
            <Layout>
              <Settings />
            </Layout>
          </RequireAuth>
        }
      />
      <Route path="*" element={<NotFound />} />
    </Routes>
  );
}
