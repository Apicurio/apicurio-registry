---
paths:
  - "ui/**"
---
# UI Development

- React + TypeScript frontend
- Build system: npm + Vite
- Subdirectories: `ui-app/` (main app), `ui-docs/` (documentation UI), `ui-editors/` (schema editors)
- Install: `cd ui && npm install`
- Dev server: `cd ui/ui-app && npm run dev`
- Build: `cd ui && npm run build`
- Linting: eslint (config in `ui-app/eslint.config.js`)
- TypeScript config: `tsconfig.json` in each ui subdirectory
- The UI build is separate from the Maven build
- UI tests: `cd ui/ui-app && npm test`
