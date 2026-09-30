# DocuEdit Web Hosting Cloud Storage Setup

## Hostinger Server: `shribalajikripadham.online`

The Android app is fully integrated with your hosting server:
`https://shribalajikripadham.online/api/docu_ai.php`

### How It Works:
1. **Cloud Upload:**
   When you tap **"Backup to Web Cloud"** or **"Cloud Hosting Backup"** inside the app:
   - Your document (PDF, JPG, or PNG) is securely sent to `https://shribalajikripadham.online/api/docu_ai.php`.
   - The file is saved directly into `/public_html/api/uploads/documents/` on your Hostinger server.
   - It instantly generates:
     - **Live Share URL:** `https://shribalajikripadham.online/api/docu_ai.php?view={doc_id}`
     - **Direct Download URL:** `https://shribalajikripadham.online/api/docu_ai.php?download={doc_id}`
     - **Live QR Code:** Anyone scanning with their phone camera opens the document immediately!

2. **Web Viewer:**
   Anyone opening the share URL in any browser (Chrome, Safari, Firefox, iPhone, Android, Laptop) gets a clean, responsive web viewer where they can view the document, print it, or download it.

3. **Deployment (if you want to update the script):**
   - Log into your Hostinger hPanel.
   - Go to **File Manager** -> `public_html/api/`.
   - Ensure `docu_ai.php` matches `server/docu_ai.php`.
   - Ensure the folder `public_html/api/uploads/documents/` exists with `755` write permissions.
