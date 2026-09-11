import { useEffect, useState } from "react";
import { useNavigate } from "react-router";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { trpc } from "@/providers/trpc";
import { LogIn, BookOpen, KeyRound } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { toast } from "sonner";

export default function Login() {
  const navigate = useNavigate();
  const { user, isLoading } = useAuth();
  
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  
  const [newUsername, setNewUsername] = useState("");
  const [newPassword, setNewPassword] = useState("");
  
  const [error, setError] = useState("");

  const { data: defaultCheck } = trpc.auth.checkDefault.useQuery();
  const isDefault = defaultCheck?.isDefault;

  useEffect(() => {
    if (!isLoading && user) {
      navigate("/", { replace: true });
    }
  }, [isLoading, navigate, user]);

  const loginMutation = trpc.auth.login.useMutation({
    onSuccess: () => {
      if (isDefault) {
        changePasswordMutation.mutate({
          currentPassword: password,
          newUsername: newUsername,
          newPassword: newPassword,
        });
      } else {
        window.location.href = "/";
      }
    },
    onError: (err) => {
      setError(err.message || "Invalid credentials");
    },
  });

  const changePasswordMutation = trpc.auth.changePassword.useMutation({
    onSuccess: () => {
      toast.success("Credentials updated successfully");
      window.location.href = "/";
    },
    onError: (err) => {
      setError(err.message || "Failed to update credentials");
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError("");

    if (!username.trim() || !password.trim()) {
      setError("Please enter both username and password");
      return;
    }

    if (isDefault) {
      if (!newUsername.trim() || !newPassword.trim()) {
        setError("Please enter new username and password");
        return;
      }
    }

    loginMutation.mutate({ username, password });
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-[#f5f0e8]">
      <div className="w-full max-w-sm px-4">
        {/* Logo */}
        <div className="flex items-center justify-center gap-3 mb-8">
          <div className="w-12 h-12 rounded-xl bg-[#c4703f] flex items-center justify-center">
            <BookOpen className="w-6 h-6 text-white" />
          </div>
          <div className="text-center">
            <h1 className="text-xl font-bold text-[#1e2a4a]">Alpha Wholesale</h1>
            <p className="text-xs text-[#3d4f6f]">ERP System</p>
          </div>
        </div>

        <Card className="border-[#d9cfc0] shadow-lg">
          <CardHeader className="text-center pb-4">
            <CardTitle className="text-lg font-semibold text-[#1e2a4a]">
              {isDefault ? "Set Up Credentials" : "Sign In"}
            </CardTitle>
            {isDefault ? (
              <p className="text-xs text-amber-600 bg-amber-50 p-2 rounded mt-2">
                You are using default credentials. Please set a new username and password.
              </p>
            ) : (
              <p className="text-xs text-[#3d4f6f]">Enter your credentials to access</p>
            )}
          </CardHeader>

          <CardContent>
            <form onSubmit={handleSubmit} className="space-y-4">
              {isDefault && (
                <div className="space-y-4 p-4 border border-amber-200 bg-amber-50/30 rounded-lg">
                  <div className="space-y-2">
                    <Label htmlFor="current-username" className="text-sm text-[#1e2a4a]">Current Username</Label>
                    <Input
                      id="current-username"
                      type="text"
                      value={username}
                      onChange={(e) => setUsername(e.target.value)}
                      placeholder="admin"
                      className="bg-white border-[#d9cfc0]"
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="current-password" className="text-sm text-[#1e2a4a]">Current Password</Label>
                    <Input
                      id="current-password"
                      type="password"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      placeholder="admin"
                      className="bg-white border-[#d9cfc0]"
                    />
                  </div>
                </div>
              )}

              {!isDefault && (
                <>
                  <div className="space-y-2">
                    <Label htmlFor="username" className="text-sm text-[#1e2a4a]">Username</Label>
                    <Input
                      id="username"
                      type="text"
                      value={username}
                      onChange={(e) => setUsername(e.target.value)}
                      placeholder="Enter username"
                      className="bg-[#f5f0e8] border-[#d9cfc0] focus:border-[#c4703f]"
                      autoComplete="username"
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="password" className="text-sm text-[#1e2a4a]">Password</Label>
                    <Input
                      id="password"
                      type="password"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      placeholder="Enter password"
                      className="bg-[#f5f0e8] border-[#d9cfc0] focus:border-[#c4703f]"
                      autoComplete="current-password"
                    />
                  </div>
                </>
              )}

              {isDefault && (
                <div className="space-y-4 p-4 border border-blue-200 bg-blue-50/30 rounded-lg">
                  <div className="space-y-2">
                    <Label htmlFor="new-username" className="text-sm text-[#1e2a4a]">New Username</Label>
                    <Input
                      id="new-username"
                      type="text"
                      value={newUsername}
                      onChange={(e) => setNewUsername(e.target.value)}
                      placeholder="Enter new username"
                      className="bg-white border-[#d9cfc0] focus:border-[#c4703f]"
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="new-password" className="text-sm text-[#1e2a4a]">New Password</Label>
                    <Input
                      id="new-password"
                      type="password"
                      value={newPassword}
                      onChange={(e) => setNewPassword(e.target.value)}
                      placeholder="Enter new password"
                      className="bg-white border-[#d9cfc0] focus:border-[#c4703f]"
                    />
                  </div>
                </div>
              )}

              {error && (
                <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded-md">{error}</p>
              )}

              <Button
                type="submit"
                className="w-full bg-[#c4703f] hover:bg-[#a85d32] text-white"
                disabled={loginMutation.isPending || changePasswordMutation.isPending}
              >
                {isDefault ? <KeyRound className="w-4 h-4 mr-2" /> : <LogIn className="w-4 h-4 mr-2" />}
                {loginMutation.isPending || changePasswordMutation.isPending 
                  ? "Processing..." 
                  : isDefault ? "Set Credentials & Login" : "Sign In"}
              </Button>
            </form>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
