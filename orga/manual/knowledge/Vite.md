- build tool and dev server
	- runs app locally, supports hot reload
	- prepares code for production
- used for Svelte, React and Vue
## What Vite Does in Your Project

### Development (`npm run dev`)
- Starts a server at `localhost:5173`
- Serves your Svelte app instantly
- Watches for changes in `.svelte` or `.ts` files
- Reloads the browser automatically

### Production (`npm run build`)
- Compiles `.svelte` + `.ts` → JS bundle
- Optimizes CSS + images
- Outputs to `dist/` folder
- You can copy `dist/` into Spring Boot’s `resources/static/`
## Analogy
Vite is like a **personal assistant for your frontend code**:
- During dev → watche files, reloads browser instantly, forwards API requests

- For production → packs everything neatly so Spring Boot can serve it efficiently