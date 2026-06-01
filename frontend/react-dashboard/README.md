# CloudPool React Dashboard

> **This is the contribution target for React / MERN stack developers.**
>
> The current admin dashboard (`frontend/dashboard/index.html`) is a single vanilla JS file.
> This directory is where the **modern React rewrite** lives — and we need your help to build it!

---

## 🎯 What We Need Built

This dashboard connects to the CloudPool backend via the official [`@cloudpool/sdk`](../../sdk/javascript) and gives users a visual control plane for their infrastructure.

### Priority Pages (pick one and open a PR!)

| Page | Route | Status | Issue |
|------|-------|--------|-------|
| Login / Register | `/auth` | 🟡 Needs work | #issue-react-auth |
| Project Dashboard | `/projects` | 🔴 Not started | #issue-react-projects |
| Database Console | `/console` | 🔴 Not started | #issue-react-console |
| File Storage | `/storage` | 🔴 Not started | #issue-react-storage |
| Vector Search | `/vector` | 🔴 Not started | #issue-react-vector |
| Compute Panel | `/compute` | 🔴 Not started | #issue-react-compute |
| Settings | `/settings` | 🔴 Not started | #issue-react-settings |

---

## 🚀 Getting Started

```bash
# 1. Install dependencies
cd frontend/react-dashboard
npm install

# 2. Set your CloudPool instance URL
cp .env.example .env
# Edit .env: VITE_CLOUDPOOL_URL=http://localhost:8080

# 3. Start dev server
npm run dev
# → Opens at http://localhost:5173
```

---

## 🏗️ Project Structure

```
react-dashboard/
├── src/
│   ├── main.tsx              # App entry point
│   ├── App.tsx               # Router + layout
│   ├── pages/                # One file per page
│   │   ├── Auth.tsx          # Login / Register
│   │   ├── Projects.tsx      # Project list
│   │   ├── Console.tsx       # Database SQL console
│   │   ├── Storage.tsx       # File manager
│   │   ├── Vector.tsx        # Vector search UI
│   │   └── Compute.tsx       # Container / serverless
│   ├── components/           # Shared UI components
│   │   ├── Sidebar.tsx
│   │   ├── Header.tsx
│   │   └── ...
│   ├── hooks/                # Custom React hooks
│   │   ├── useAuth.ts
│   │   ├── useProjects.ts
│   │   └── ...
│   └── lib/
│       └── cloudpool.ts      # SDK instance (singleton)
├── package.json
├── vite.config.ts
└── .env.example
```

---

## 🔗 API Integration

Use the official SDK — **no direct fetch calls needed**:

```tsx
import { cp } from '../lib/cloudpool';

// In a React Query hook:
const { data: projects } = useQuery({
  queryKey: ['projects'],
  queryFn: () => cp.projects.list(),
});

// Upload a file:
const handleUpload = async (file: File) => {
  const result = await cp.storage.upload('my-bucket', file);
  console.log('Uploaded:', result.originalName);
};

// Semantic search:
const results = await cp.vector.searchFiles('invoices from 2025');
```

---

## 🎨 Design Guidelines

- Use **Tailwind CSS** or **CSS Modules** (your choice)
- Dark mode support preferred
- Follow the color scheme: primary `#6366f1` (indigo), surface dark `#0f172a`
- Icons: `lucide-react` (already in dependencies)
- Keep components small and focused (< 150 lines per component)

---

## ✅ PR Checklist

- [ ] Page renders without errors
- [ ] Connects to real CloudPool API (not mocked)
- [ ] Handles loading and error states
- [ ] Mobile responsive
- [ ] TypeScript — no `any` types

---

## 💬 Questions?

Join `#react` on [Discord](https://discord.gg/gzcnkE7yN) or open an issue.
