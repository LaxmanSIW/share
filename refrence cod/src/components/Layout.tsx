import { useState, useEffect, type FormEvent } from "react";
import { Link, useLocation, useNavigate } from "react-router";
import {
  LayoutDashboard,
  BookOpen,
  Users,
  BarChart3,
  Upload,
  Menu,
  ChevronLeft,
  ChevronRight,
  LogOut,
  Info,
  KeyRound,
  Settings,
  FileText,
  Tag,
  Truck,
  FolderTree
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Sheet, SheetContent, SheetTrigger } from "@/components/ui/sheet";
import { useAuth } from "@/hooks/useAuth";
import { useToast } from "@/hooks/use-toast";
import { trpc } from "@/providers/trpc";
 
const navItems = [
  { path: "/", label: "Dashboard", icon: LayoutDashboard, shortcut: "0" },
  { path: "/bills", label: "Tax Invoices", icon: FileText, shortcut: "1" },
  {path: "/categories", label: "Categories", icon: FolderTree, shortcut: "2" },
  { path: "/items", label: "Item Catalog", icon: Tag, shortcut: "3" },
  { path: "/transports", label: "Transports", icon: Truck, shortcut: "4" },
  { path: "/transactions", label: "Transactions", icon: BookOpen, shortcut: "5" },
  { path: "/buyers", label: "Buyers", icon: Users, shortcut: "6" },
  { path: "/reports", label: "Reports", icon: BarChart3, shortcut: "7" },
  { path: "/bulk-upload", label: "Bulk Upload", icon: Upload, shortcut: "8" },
  { path: "/about", label: "About", icon: Info, shortcut: "9" },
  { path: "/settings", label: "Settings", icon: Settings, shortcut: "10" },
  
];

export default function Layout({ children }: { children: React.ReactNode }) {
  const location = useLocation();
  const navigate = useNavigate();
  const [collapsed, setCollapsed] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [showPasswordDialog, setShowPasswordDialog] = useState(false);
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [passwordError, setPasswordError] = useState("");
  const { user, logout } = useAuth();
  const { toast } = useToast();

  const changePasswordMutation = trpc.auth.changePassword.useMutation({
    onSuccess: () => {
      toast({ title: "Password updated", description: "Your password was changed successfully." });
      setShowPasswordDialog(false);
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
      setPasswordError("");
    },
    onError: (err) => {
      setPasswordError(err.message || "Unable to change password");
    },
  });

  const handlePasswordChange = (e: FormEvent) => {
    e.preventDefault();
    setPasswordError("");
    if (!currentPassword.trim() || !newPassword.trim() || !confirmPassword.trim()) {
      setPasswordError("Please fill in all password fields");
      return;
    }
    if (newPassword !== confirmPassword) {
      setPasswordError("New passwords do not match");
      return;
    }
    changePasswordMutation.mutate({ currentPassword, newPassword });
  };

  // Keyboard shortcuts
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.ctrlKey && e.key >= "0" && e.key <= "9") {
        e.preventDefault();
        const targetItem = navItems.find((item) => item.shortcut === e.key);
        if (targetItem) navigate(targetItem.path);
      }
      if (e.ctrlKey && e.key === "b") {
        e.preventDefault();
        setCollapsed((c) => !c);
      }
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, [navigate]);

  const sidebarContent = (
    <div className="flex h-full flex-col">
      {/* Brand - clickable for toggle */}
      <div
        className="flex items-center gap-3 px-4 py-5 border-b border-[#2a3a5c] cursor-pointer hover:bg-[#2a3a5c]/30 transition-colors"
        onClick={() => setCollapsed(!collapsed)}
        title="Click to toggle sidebar"
      >
        <div className="w-8 h-8 rounded-lg bg-[#c4703f] flex items-center justify-center flex-shrink-0">
          <BookOpen className="w-4 h-4 text-white" />
        </div>
        {!collapsed && (
          <div className="overflow-hidden flex-1">
            <h1 className="text-white font-semibold text-sm leading-tight whitespace-nowrap">Alpha Wholesale</h1>
            <p className="text-[#8b9bb4] text-[11px] whitespace-nowrap">ERP System</p>
          </div>
        )}
        {!collapsed && (
          <div className="text-[#8b9bb4]">
            {collapsed ? <ChevronRight className="w-3 h-3" /> : <ChevronLeft className="w-3 h-3" />}
          </div>
        )}
      </div>

      {/* Navigation */}
      <nav className="flex-1 py-3 px-2 space-y-1">
        {navItems.map((item) => {
          const isActive = location.pathname === item.path;
          const Icon = item.icon;
          return (
            <Link
              key={item.path}
              to={item.path}
              onClick={() => setMobileOpen(false)}
              className={`flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all duration-150 group relative ${
                isActive
                  ? "bg-[#c4703f]/15 text-[#d4895a] border-l-[3px] border-[#c4703f]"
                  : "text-[#b8c4d4]/70 hover:bg-[#2a3a5c] hover:text-white border-l-[3px] border-transparent"
              }`}
            >
              <Icon className="w-5 h-5 flex-shrink-0" />
              {!collapsed && <span className="truncate">{item.label}</span>}
              {!collapsed && (
                <kbd className="ml-auto text-[10px] bg-[#1e2a4a] text-[#8b9bb4] px-1.5 py-0.5 rounded font-mono opacity-0 group-hover:opacity-100 transition-opacity">
                  Ctrl+{item.shortcut}
                </kbd>
              )}
            </Link>
          );
        })}
      </nav>

      {/* User Section */}
      <div className="border-t border-[#2a3a5c] p-3">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-full bg-[#c4703f]/20 flex items-center justify-center flex-shrink-0">
            <span className="text-[#d4895a] text-xs font-semibold">
              {user?.name?.charAt(0)?.toUpperCase() || "A"}
            </span>
          </div>
          {!collapsed && (
            <div className="flex-1 min-w-0">
              <p className="text-white text-sm font-medium truncate">{user?.name || "admin"}</p>
              <p className="text-[#8b9bb4] text-[11px] capitalize">{user?.role || "admin"}</p>
            </div>
          )}
          {!collapsed && (
            <div className="flex items-center gap-1">
              <button
                onClick={() => setShowPasswordDialog(true)}
                className="p-1.5 rounded hover:bg-[#2a3a5c] text-[#8b9bb4] hover:text-[#d4895a] transition-colors"
                title="Change password"
              >
                <KeyRound className="w-4 h-4" />
              </button>
              <button
                onClick={logout}
                className="p-1.5 rounded hover:bg-[#2a3a5c] text-[#8b9bb4] hover:text-red-400 transition-colors"
                title="Logout"
              >
                <LogOut className="w-4 h-4" />
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );

  return (
    <div className="flex h-screen bg-[#f5f0e8] overflow-hidden print:bg-white print:h-auto print:overflow-visible">
      {/* Desktop Sidebar */}
      <aside
        className={`hidden lg:flex flex-col bg-[#1e2a4a] border-r border-[#2a3a5c] transition-all duration-200 flex-shrink-0 relative print:hidden ${
          collapsed ? "w-16" : "w-64"
        }`}
      >
        {sidebarContent}
      </aside>

      {/* Mobile Sidebar */}
      <Sheet open={mobileOpen} onOpenChange={setMobileOpen}>
        <SheetTrigger asChild className="lg:hidden print:hidden">
          <Button
            variant="ghost"
            size="icon"
            className="fixed top-4 left-4 z-50 bg-[#1e2a4a] text-white hover:bg-[#2a3a5c] print:hidden"
          >
            <Menu className="w-5 h-5" />
          </Button>
        </SheetTrigger>
        <SheetContent side="left" className="w-64 p-0 bg-[#1e2a4a] border-[#2a3a5c] print:hidden">
          {sidebarContent}
        </SheetContent>
      </Sheet>

      {/* Main Content */}
      <main className="flex-1 flex flex-col overflow-hidden print:overflow-visible print:h-auto">
        <div className="flex-1 overflow-y-auto scrollbar-thin p-4 lg:p-6 print:overflow-visible print:p-0">
          {children}
        </div>
      </main>
      <Dialog open={showPasswordDialog} onOpenChange={setShowPasswordDialog}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Change Password</DialogTitle>
            <DialogDescription>Update your password for the local admin account.</DialogDescription>
          </DialogHeader>
          <form onSubmit={handlePasswordChange} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="current-password">Current password</Label>
              <Input
                id="current-password"
                type="password"
                value={currentPassword}
                onChange={(e) => setCurrentPassword(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="new-password">New password</Label>
              <Input
                id="new-password"
                type="password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="confirm-password">Confirm password</Label>
              <Input
                id="confirm-password"
                type="password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
              />
            </div>
            {passwordError && <p className="text-sm text-red-600">{passwordError}</p>}
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setShowPasswordDialog(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={changePasswordMutation.isPending}>
                {changePasswordMutation.isPending ? "Updating..." : "Update password"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
