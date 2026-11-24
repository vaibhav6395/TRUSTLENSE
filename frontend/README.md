# TRUSTLENS frontend

React + TypeScript + Vite development UI.

## Run
```bash
npm install
npm run dev
```

Open http://localhost:5173. Configure `VITE_API_BASE_URL` in a local `.env` file if the API runs elsewhere.

The current screens include a dashboard, searchable synthetic document list, synthetic people directory, policy-sandbox input preview, decision examples, and audit timeline. These are frontend scaffolds: sample rows are not live records and the sandbox does not make authorization decisions. Upload/download and administrative actions remain disabled until their backend endpoints and server-side authorization are implemented.
