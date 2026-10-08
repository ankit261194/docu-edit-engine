package com.docu.editor.core.pcdrop

object PcDropWebTemplate {

    fun getHtml(): String {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>DocuEdit — PC Drop High-Speed Wi-Fi Hub</title>
    <style>
        :root {
            --primary: #2563EB;
            --primary-hover: #1D4ED8;
            --bg: #0F172A;
            --surface: #1E293B;
            --surface-card: rgba(30, 41, 59, 0.7);
            --border: #334155;
            --text-main: #F8FAFC;
            --text-muted: #94A3B8;
            --accent-green: #10B981;
            --accent-red: #EF4444;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
        body { background: var(--bg); color: var(--text-main); min-height: 100vh; display: flex; flex-direction: column; }
        
        /* PIN Lock Modal */
        #pinModal {
            position: fixed; inset: 0; background: rgba(15, 23, 42, 0.95); backdrop-filter: blur(12px);
            display: flex; align-items: center; justify-content: center; z-index: 1000;
        }
        .pin-box {
            background: var(--surface); border: 1px solid var(--border); border-radius: 20px;
            padding: 40px; width: 90%; max-width: 440px; text-align: center; box-shadow: 0 25px 50px -12px rgba(0,0,0,0.5);
        }
        .pin-icon { width: 64px; height: 64px; background: rgba(37, 99, 235, 0.15); border-radius: 16px; display: inline-flex; align-items: center; justify-content: center; margin-bottom: 20px; color: var(--primary); font-size: 28px; }
        .pin-inputs { display: flex; gap: 12px; justify-content: center; margin: 24px 0; }
        .pin-digit {
            width: 56px; height: 64px; background: #0F172A; border: 2px solid var(--border); border-radius: 12px;
            color: #fff; font-size: 28px; text-align: center; font-weight: bold; outline: none; transition: border-color 0.2s;
        }
        .pin-digit:focus { border-color: var(--primary); }
        .pin-error { color: var(--accent-red); font-size: 14px; margin-top: 10px; display: none; }

        /* Navbar */
        header {
            background: var(--surface); border-bottom: 1px solid var(--border);
            padding: 16px 32px; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 16px;
        }
        .brand { display: flex; align-items: center; gap: 12px; font-weight: 700; font-size: 20px; }
        .brand-badge { background: rgba(16, 185, 129, 0.15); color: var(--accent-green); font-size: 12px; padding: 4px 10px; border-radius: 20px; display: flex; align-items: center; gap: 6px; }
        .dot { width: 8px; height: 8px; background: var(--accent-green); border-radius: 50%; animation: pulse 2s infinite; }
        @keyframes pulse { 0% { opacity: 1; transform: scale(1); } 50% { opacity: 0.4; transform: scale(0.9); } 100% { opacity: 1; transform: scale(1); } }
        
        .header-actions { display: flex; gap: 12px; align-items: center; }
        .btn {
            background: var(--primary); color: white; border: none; padding: 10px 18px; border-radius: 10px;
            font-weight: 600; font-size: 14px; cursor: pointer; transition: all 0.2s; display: inline-flex; align-items: center; gap: 8px; text-decoration: none;
        }
        .btn:hover { background: var(--primary-hover); }
        .btn-outline { background: transparent; border: 1px solid var(--border); color: var(--text-main); }
        .btn-outline:hover { background: rgba(255,255,255,0.05); }

        /* Main Container */
        main { max-width: 1280px; width: 100%; margin: 0 auto; padding: 32px 24px; flex: 1; }

        /* Drag & Drop Upload Zone */
        .dropzone {
            border: 2px dashed var(--border); border-radius: 16px; background: var(--surface-card);
            padding: 32px; text-align: center; cursor: pointer; transition: all 0.2s; margin-bottom: 32px;
        }
        .dropzone:hover, .dropzone.dragover { border-color: var(--primary); background: rgba(37, 99, 235, 0.08); }
        .dropzone-icon { font-size: 40px; margin-bottom: 12px; }
        .dropzone h3 { font-size: 18px; margin-bottom: 6px; }
        .dropzone p { color: var(--text-muted); font-size: 14px; }
        #fileInput { display: none; }
        
        /* Search & Filter Bar */
        .toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; flex-wrap: wrap; gap: 16px; }
        .search-box {
            background: var(--surface); border: 1px solid var(--border); border-radius: 10px;
            padding: 10px 16px; color: white; width: 320px; max-width: 100%; outline: none; font-size: 14px;
        }
        .search-box:focus { border-color: var(--primary); }

        /* Document Grid */
        .doc-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 20px; }
        .doc-card {
            background: var(--surface); border: 1px solid var(--border); border-radius: 14px;
            overflow: hidden; transition: transform 0.2s, box-shadow 0.2s; display: flex; flex-direction: column;
        }
        .doc-card:hover { transform: translateY(-3px); box-shadow: 0 10px 25px -5px rgba(0,0,0,0.3); border-color: #475569; }
        .thumb-wrap { width: 100%; height: 200px; background: #000; overflow: hidden; position: relative; }
        .thumb-img { width: 100%; height: 100%; object-fit: contain; }
        .format-tag {
            position: absolute; top: 10px; right: 10px; background: rgba(0,0,0,0.75);
            backdrop-filter: blur(4px); font-size: 11px; font-weight: 700; padding: 3px 8px; border-radius: 6px;
        }
        .doc-body { padding: 16px; flex: 1; display: flex; flex-direction: column; }
        .doc-title { font-size: 15px; font-weight: 600; margin-bottom: 8px; word-break: break-all; }
        .doc-meta { display: flex; justify-content: space-between; color: var(--text-muted); font-size: 12px; margin-bottom: 14px; }
        .doc-actions { margin-top: auto; display: flex; gap: 8px; }
        .btn-sm { flex: 1; padding: 8px; font-size: 13px; text-align: center; justify-content: center; }

        /* Upload Toast */
        #toast {
            position: fixed; bottom: 24px; right: 24px; background: #10B981; color: white;
            padding: 12px 20px; border-radius: 10px; font-weight: 600; font-size: 14px;
            box-shadow: 0 10px 20px rgba(0,0,0,0.3); transform: translateY(100px); opacity: 0; transition: all 0.3s; z-index: 2000;
        }
        #toast.show { transform: translateY(0); opacity: 1; }

        /* Empty state */
        .empty-state { text-align: center; padding: 60px 20px; color: var(--text-muted); grid-column: 1 / -1; }
    </style>
</head>
<body>

    <!-- 4-Digit Security PIN Modal -->
    <div id="pinModal">
        <div class="pin-box">
            <div class="pin-icon">🔒</div>
            <h2>Device Verification</h2>
            <p style="color: var(--text-muted); font-size: 14px; margin-top: 8px;">
                Enter the 4-digit security PIN currently shown on your phone's DocuEdit screen.
            </p>
            <div class="pin-inputs">
                <input type="text" maxlength="1" class="pin-digit" autofocus>
                <input type="text" maxlength="1" class="pin-digit">
                <input type="text" maxlength="1" class="pin-digit">
                <input type="text" maxlength="1" class="pin-digit">
            </div>
            <button class="btn" id="btnVerify" style="width: 100%; justify-content: center; padding: 14px;">Connect to Phone</button>
            <div class="pin-error" id="pinError">Invalid PIN. Please check your phone screen.</div>
        </div>
    </div>

    <!-- Top Navbar -->
    <header>
        <div class="brand">
            <span style="font-size: 24px;">📄</span>
            <span>DocuEdit <span style="font-weight: 300; opacity: 0.8;">PC Drop</span></span>
            <div class="brand-badge"><span class="dot"></span> Wi-Fi High Speed</div>
        </div>
        <div class="header-actions">
            <button class="btn btn-outline" id="btnRefresh">🔄 Refresh</button>
            <a class="btn" id="btnDownloadAll" href="/api/download-all-zip">📦 Download All (ZIP)</a>
        </div>
    </header>

    <!-- Main Workspace -->
    <main>
        <!-- Drag & Drop Upload to Phone -->
        <div class="dropzone" id="dropzone">
            <div class="dropzone-icon">📥</div>
            <h3>Send Files to Phone</h3>
            <p>Drag & drop PDF or images here, or <span style="color: var(--primary); text-decoration: underline;">click to browse</span></p>
            <input type="file" id="fileInput" multiple accept="image/*,application/pdf">
        </div>

        <!-- Search & Count Toolbar -->
        <div class="toolbar">
            <input type="text" class="search-box" id="searchBox" placeholder="Search documents by title...">
            <div style="color: var(--text-muted); font-size: 14px;" id="countLabel">Loading documents...</div>
        </div>

        <!-- Documents Grid -->
        <div class="doc-grid" id="docGrid">
            <!-- Dynamically populated -->
        </div>
    </main>

    <div id="toast">File uploaded to phone successfully!</div>

    <script>
        var allDocs = [];
        var authToken = sessionStorage.getItem('pcdrop_token');

        // PIN digits auto-tab
        var digits = document.querySelectorAll('.pin-digit');
        digits.forEach(function(input, index) {
            input.addEventListener('input', function(e) {
                if (e.target.value.length === 1 && index < digits.length - 1) {
                    digits[index + 1].focus();
                }
                if (index === 3 && e.target.value.length === 1) {
                    verifyPin();
                }
            });
            input.addEventListener('keydown', function(e) {
                if (e.key === 'Backspace' && !e.target.value && index > 0) {
                    digits[index - 1].focus();
                }
            });
        });

        document.getElementById('btnVerify').addEventListener('click', verifyPin);

        async function verifyPin() {
            var pin = Array.from(digits).map(function(d) { return d.value; }).join('');
            if (pin.length < 4) return;
            
            try {
                var res = await fetch('/api/auth', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ pin: pin })
                });
                var data = await res.json();
                if (data.success) {
                    sessionStorage.setItem('pcdrop_token', data.token);
                    authToken = data.token;
                    document.getElementById('pinModal').style.display = 'none';
                    loadDocuments();
                } else {
                    document.getElementById('pinError').style.display = 'block';
                    digits.forEach(function(d) { d.value = ''; });
                    digits[0].focus();
                }
            } catch (err) {
                alert('Connection error: ' + err.message);
            }
        }

        // Auto authenticate if token exists
        if (authToken) {
            document.getElementById('pinModal').style.display = 'none';
            loadDocuments();
        }

        async function loadDocuments() {
            try {
                var res = await fetch('/api/documents', {
                    headers: { 'Authorization': authToken || '' }
                });
                if (res.status === 401) {
                    sessionStorage.removeItem('pcdrop_token');
                    document.getElementById('pinModal').style.display = 'flex';
                    return;
                }
                allDocs = await res.json();
                renderDocuments(allDocs);
            } catch (e) {
                console.error("Failed to load docs", e);
            }
        }

        function renderDocuments(docs) {
            var grid = document.getElementById('docGrid');
            grid.innerHTML = '';
            document.getElementById('countLabel').innerText = docs.length + ' Document(s) on Phone';

            if (docs.length === 0) {
                grid.innerHTML = '<div class="empty-state"><h3>No documents found</h3><p>Scan a document on your phone or drop a file above.</p></div>';
                return;
            }

            docs.forEach(function(doc) {
                var isPdf = doc.isPdf;
                var card = document.createElement('div');
                card.className = 'doc-card';
                card.innerHTML = 
                    '<div class="thumb-wrap">' +
                        '<img class="thumb-img" src="/api/thumb?id=' + encodeURIComponent(doc.id) + '&token=' + encodeURIComponent(authToken) + '" alt="' + doc.title + '" onerror="this.style.display=\'none\'">' +
                        '<span class="format-tag">' + (isPdf ? 'PDF' : 'JPG') + '</span>' +
                    '</div>' +
                    '<div class="doc-body">' +
                        '<div class="doc-title">' + doc.title + '</div>' +
                        '<div class="doc-meta">' +
                            '<span>📅 ' + doc.formattedDate + '</span>' +
                            '<span>💾 ' + doc.formattedSize + '</span>' +
                        '</div>' +
                        '<div class="doc-actions">' +
                            '<a href="/api/download?id=' + encodeURIComponent(doc.id) + '&token=' + encodeURIComponent(authToken) + '" class="btn btn-sm" download>⬇️ Download</a>' +
                        '</div>' +
                    '</div>';
                grid.appendChild(card);
            });
        }

        // Live Search Filter
        document.getElementById('searchBox').addEventListener('input', function(e) {
            var query = e.target.value.toLowerCase();
            var filtered = allDocs.filter(function(d) { return d.title.toLowerCase().indexOf(query) !== -1; });
            renderDocuments(filtered);
        });

        document.getElementById('btnRefresh').addEventListener('click', loadDocuments);

        // Drag & Drop Upload
        var dropzone = document.getElementById('dropzone');
        var fileInput = document.getElementById('fileInput');

        dropzone.addEventListener('click', function() { fileInput.click(); });
        dropzone.addEventListener('dragover', function(e) { e.preventDefault(); dropzone.classList.add('dragover'); });
        dropzone.addEventListener('dragleave', function() { dropzone.classList.remove('dragover'); });
        dropzone.addEventListener('drop', function(e) {
            e.preventDefault();
            dropzone.classList.remove('dragover');
            if (e.dataTransfer.files.length) uploadFiles(e.dataTransfer.files);
        });
        fileInput.addEventListener('change', function() {
            if (fileInput.files.length) uploadFiles(fileInput.files);
        });

        async function uploadFiles(files) {
            for (var i = 0; i < files.length; i++) {
                var file = files[i];
                var formData = new FormData();
                formData.append('file', file);
                try {
                    var res = await fetch('/api/upload', {
                        method: 'POST',
                        headers: { 'Authorization': authToken || '' },
                        body: formData
                    });
                    if (res.ok) {
                        showToast('Uploaded: ' + file.name);
                    }
                } catch (e) {
                    alert('Upload failed for ' + file.name);
                }
            }
            loadDocuments();
        }

        function showToast(msg) {
            var toast = document.getElementById('toast');
            toast.innerText = msg;
            toast.classList.add('show');
            setTimeout(function() { toast.classList.remove('show'); }, 3000);
        }

        // Auto poll for newly scanned documents every 5 seconds
        setInterval(function() {
            if (authToken) loadDocuments();
        }, 5000);
    </script>
</body>
</html>
        """.trimIndent()
    }
}
