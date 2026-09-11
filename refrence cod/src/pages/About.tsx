import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  BookOpen,
  Shield,
  Code,
  Terminal,
  Rocket
} from "lucide-react";

function Section({
  icon: Icon,
  title,
  children,
}: {
  icon: React.ElementType;
  title: string;
  children: React.ReactNode;
}) {
  return (
    <Card className="border-[#d9cfc0] bg-white">
      <CardHeader className="pb-3">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-[#c4703f]/10 flex items-center justify-center flex-shrink-0">
            <Icon className="w-4 h-4 text-[#c4703f]" />
          </div>
          <CardTitle className="text-lg font-semibold text-[#1e2a4a]">{title}</CardTitle>
        </div>
      </CardHeader>
      <CardContent className="pt-0 text-sm text-[#3d4f6f] leading-relaxed space-y-3">
        {children}
      </CardContent>
    </Card>
  );
}

function CodeBlock({ children }: { children: string }) {
  return (
    <pre className="bg-[#1e2a4a] text-[#b8c4d4] p-3 rounded-lg text-xs font-mono overflow-x-auto mt-2">
      <code>{children}</code>
    </pre>
  );
}

export default function About() {
  return (
    <div className="max-w-4xl space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-3xl font-bold text-[#1e2a4a]">About</h1>
        <p className="text-sm text-[#3d4f6f] mt-1">
          Alpha Wholesale ERP System - Technical Documentation
        </p>
      </div>

      {/* System Overview */}
      <Section icon={BookOpen} title="System Overview">
        <p>
          AlphaERP is a full-stack enterprise resource planning system designed for wholesale garment businesses.
          It manages buyers, transactions (CC and CS books), generates financial reports, and provides
          risk analysis for credit management.
        </p>
        <div className="flex flex-wrap gap-2 pt-1">
          {["React 19", "TypeScript", "Tailwind CSS", "shadcn/ui", "tRPC", "SQLite DB", "Hono", "Node.js"].map((tech) => (
            <span key={tech} className="px-2 py-1 bg-[#f5f0e8] rounded-md text-xs font-mono text-[#1e2a4a]">
              {tech}
            </span>
          ))}
        </div>
      </Section>

      {/* Risk Calculation Logic */}
      <Section icon={Shield} title="Risk Calculation Logic">
        <p>
          The risk score for each buyer is calculated based on their <strong>credit utilization ratio</strong>,
          which compares their outstanding balance against their assigned credit limit.
        </p>
        <div className="bg-[#f5f0e8] rounded-lg p-4 space-y-2">
          <h4 className="font-semibold text-[#1e2a4a] text-xs uppercase tracking-wider">Formula</h4>
          <p className="font-mono text-xs">
            Utilization = Outstanding Balance / Credit Limit
          </p>
          <div className="space-y-1 pt-1">
            <div className="flex items-center gap-2 text-xs">
              <span className="w-2 h-2 rounded-full bg-red-500 flex-shrink-0" />
              <strong>High Risk (Score: 2):</strong> Utilization &gt; 80%
            </div>
            <div className="flex items-center gap-2 text-xs">
              <span className="w-2 h-2 rounded-full bg-yellow-500 flex-shrink-0" />
              <strong>Medium Risk (Score: 5):</strong> Utilization 40% - 80%
            </div>
            <div className="flex items-center gap-2 text-xs">
              <span className="w-2 h-2 rounded-full bg-green-500 flex-shrink-0" />
              <strong>Low Risk (Score: 8):</strong> Utilization &lt; 40%
            </div>
          </div>
        </div>
        <p className="text-xs text-[#8b9bb4]">
          If a buyer has no credit limit set, the risk score defaults to Medium (5).
          The risk badge color changes dynamically based on the calculated score.
        </p>
      </Section>

      {/* Developer Guide */}
      <Section icon={Code} title="Developer Guide">
        <div className="space-y-4">
          <div>
            <h4 className="font-semibold text-[#1e2a4a] mb-1 flex items-center gap-2">
              <Terminal className="w-3.5 h-3.5" />
              How to Setup & Run Locally
            </h4>
            <CodeBlock>{`# Clone the repository
git clone https://github.com/LaxmanSIW/AlphaERP.git
cd AlphaERP

# Install dependencies
npm install

# Start development server
npm run dev

# Format code (Optional)
npm run format`}</CodeBlock>
          </div>

          <div>
            <h4 className="font-semibold text-[#1e2a4a] mb-1 flex items-center gap-2">
              <Rocket className="w-3.5 h-3.5" />
              How to Deploy
            </h4>
            <p className="text-sm text-[#3d4f6f] mb-2">
              The application provides a build script that bundles both the React frontend and Node.js backend.
            </p>
            <CodeBlock>{`# Build for production
npm run build

# Start production server
npm start`}</CodeBlock>
          </div>
        </div>
      </Section>
    </div>
  );
}
