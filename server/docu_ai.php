<?php
/**
 * DocuEdit Enterprise Cloud API & Web Document Viewer
 * Hostinger Server Deployment for: shribalajikripadham.online
 * Location: /public_html/api/docu_ai.php
 */

@ini_set('upload_max_filesize', '128M');
@ini_set('post_max_size', '128M');
@ini_set('memory_limit', '256M');
@ini_set('max_execution_time', '300');

header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization, X-Docu-Token');

define('DOCU_SEC_TOKEN', 'balaji_docu_secure_token_8971f92a3b4c');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

$storageDir = __DIR__ . '/uploads/documents/';
if (!is_dir($storageDir)) {
    @mkdir($storageDir, 0755, true);
}

$metaFile = $storageDir . 'meta_registry.json';

function getHostUrl() {
    $protocol = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off' || $_SERVER['SERVER_PORT'] == 443) ? "https://" : "http://";
    $domainName = $_SERVER['HTTP_HOST'];
    return $protocol . $domainName;
}

// 1. Handle Direct Download
if (isset($_GET['download'])) {
    $docId = preg_replace('/[^a-zA-Z0-9_-]/', '', $_GET['download']);
    $files = glob($storageDir . $docId . '.*');
    if (!empty($files) && file_exists($files[0])) {
        $filePath = $files[0];
        $ext = pathinfo($filePath, PATHINFO_EXTENSION);
        $mime = ($ext === 'pdf') ? 'application/pdf' : (($ext === 'png') ? 'image/png' : 'image/jpeg');
        
        header('Content-Description: File Transfer');
        header('Content-Type: ' . $mime);
        header('Content-Disposition: attachment; filename="' . basename($filePath) . '"');
        header('Expires: 0');
        header('Cache-Control: must-revalidate');
        header('Pragma: public');
        header('Content-Length: ' . filesize($filePath));
        readfile($filePath);
        exit;
    } else {
        http_response_code(404);
        echo "Document not found.";
        exit;
    }
}

// 2. Handle Responsive Web Viewer
if (isset($_GET['view'])) {
    $docId = preg_replace('/[^a-zA-Z0-9_-]/', '', $_GET['view']);
    $files = glob($storageDir . $docId . '.*');
    if (!empty($files) && file_exists($files[0])) {
        $filePath = $files[0];
        $ext = strtolower(pathinfo($filePath, PATHINFO_EXTENSION));
        $fileSize = round(filesize($filePath) / 1024, 1) . ' KB';
        $downloadUrl = getHostUrl() . '/api/docu_ai.php?download=' . urlencode($docId);
        $fileDirectUrl = getHostUrl() . '/api/uploads/documents/' . basename($filePath);
        $title = htmlspecialchars(ucwords(str_replace('_', ' ', $docId)));
        
        // Find title in meta_registry if exists
        if (file_exists($metaFile)) {
            $registry = json_decode(file_get_contents($metaFile), true) ?: [];
            if (isset($registry[$docId]['title'])) {
                $title = htmlspecialchars($registry[$docId]['title']);
            }
        }
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title><?= $title ?> - DocuEdit Cloud</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
        body { background: #0F172A; color: #F8FAFC; min-height: 100vh; display: flex; flex-direction: column; }
        header { background: #1E293B; border-bottom: 1px solid #334155; padding: 14px 20px; display: flex; align-items: center; justify-content: space-between; }
        .logo { font-size: 18px; font-weight: 800; color: #38BDF8; display: flex; align-items: center; gap: 8px; }
        .actions { display: flex; gap: 10px; }
        .btn { padding: 8px 16px; border-radius: 8px; font-size: 13px; font-weight: 600; text-decoration: none; cursor: pointer; border: none; transition: 0.2s; display: inline-flex; align-items: center; gap: 6px; }
        .btn-primary { background: #2563EB; color: #FFF; }
        .btn-primary:hover { background: #1D4ED8; }
        .btn-secondary { background: #334155; color: #E2E8F0; }
        .btn-secondary:hover { background: #475569; }
        main { flex: 1; padding: 16px; display: flex; flex-direction: column; align-items: center; justify-content: center; }
        .viewer-container { width: 100%; max-width: 900px; background: #1E293B; border-radius: 16px; border: 1px solid #334155; overflow: hidden; box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.5); }
        .viewer-header { padding: 16px 20px; border-bottom: 1px solid #334155; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 10px; }
        .doc-title { font-size: 16px; font-weight: 700; color: #F1F5F9; }
        .doc-meta { font-size: 12px; color: #94A3B8; }
        .preview-box { min-height: 600px; width: 100%; display: flex; align-items: center; justify-content: center; background: #0B0F19; }
        iframe { width: 100%; height: 75vh; border: none; }
        img.preview-img { max-width: 100%; max-height: 75vh; object-fit: contain; padding: 12px; }
        footer { text-align: center; padding: 14px; font-size: 12px; color: #64748B; border-top: 1px solid #1E293B; }
    </style>
</head>
<body>
    <header>
        <div class="logo">
            <span>📄</span> DocuEdit Cloud Storage
        </div>
        <div class="actions">
            <a href="<?= $downloadUrl ?>" class="btn btn-primary" download>⬇️ Download (<?= $fileSize ?>)</a>
            <button onclick="window.print()" class="btn btn-secondary">🖨️ Print</button>
        </div>
    </header>
    <main>
        <div class="viewer-container">
            <div class="viewer-header">
                <div>
                    <div class="doc-title"><?= $title ?></div>
                    <div class="doc-meta">Stored on shribalajikripadham.online • <?= strtoupper($ext) ?> • <?= $fileSize ?></div>
                </div>
                <button onclick="navigator.clipboard.writeText(window.location.href); alert('Share link copied!');" class="btn btn-secondary">🔗 Copy Link</button>
            </div>
            <div class="preview-box">
                <?php if ($ext === 'pdf'): ?>
                    <iframe src="<?= $fileDirectUrl ?>#toolbar=1" type="application/pdf"></iframe>
                <?php else: ?>
                    <img src="<?= $fileDirectUrl ?>" alt="<?= $title ?>" class="preview-img" />
                <?php endif; ?>
            </div>
        </div>
    </main>
    <footer>
        Protected by DocuEdit Cloud &copy; <?= date('Y') ?> • shribalajikripadham.online
    </footer>
</body>
</html>
<?php
        exit;
    } else {
        http_response_code(404);
        echo "<h2 style='font-family:sans-serif;text-align:center;margin-top:50px;'>Document not found or expired.</h2>";
        exit;
    }
}

// 3. API Actions (JSON)
header('Content-Type: application/json; charset=utf-8');

$input = file_get_contents('php://input');
$data = json_decode($input, true) ?: [];
$action = $data['action'] ?? $_GET['action'] ?? '';

// Health Ping
if (empty($action) || $action === 'ping') {
    echo json_encode([
        'success' => true,
        'service' => 'DocuEdit Smart AI & Cloud',
        'hosting' => 'shribalajikripadham.online',
        'storage' => 'active',
        'model' => 'Smart AI Vision',
        'timestamp' => time()
    ]);
    exit;
}

function isAuthorizedRequest($data) {
    $headerToken = $_SERVER['HTTP_X_DOCU_TOKEN'] ?? '';
    $payloadToken = $data['token'] ?? '';
    return ($headerToken === DOCU_SEC_TOKEN || $payloadToken === DOCU_SEC_TOKEN);
}

// Document Cloud Upload
if ($action === 'cloud_upload') {
    if (!isAuthorizedRequest($data)) {
        http_response_code(401);
        echo json_encode(['success' => false, 'error' => 'Unauthorized: Invalid access token']);
        exit;
    }
    $base64 = $data['file_base64'] ?? '';
    $fileType = strtolower($data['file_type'] ?? 'pdf');
    $title = trim($data['title'] ?? 'Document_' . time());
    $pagesCount = intval($data['pages_count'] ?? 1);

    if (empty($base64)) {
        http_response_code(400);
        echo json_encode(['success' => false, 'error' => 'Missing file_base64 payload']);
        exit;
    }

    $binary = base64_decode($base64);
    if ($binary === false) {
        http_response_code(400);
        echo json_encode(['success' => false, 'error' => 'Invalid base64 encoding']);
        exit;
    }

    $cleanExt = ($fileType === 'png') ? 'png' : (($fileType === 'jpg' || $fileType === 'jpeg') ? 'jpg' : 'pdf');
    $docId = 'doc_' . date('Ymd_His') . '_' . substr(md5(uniqid()), 0, 6);
    $fileName = $docId . '.' . $cleanExt;
    $targetPath = $storageDir . $fileName;

    if (file_put_contents($targetPath, $binary) === false) {
        http_response_code(500);
        echo json_encode(['success' => false, 'error' => 'Failed to write file to storage directory']);
        exit;
    }

    $fileSize = filesize($targetPath);
    $sizeFormatted = ($fileSize > 1048576) ? round($fileSize / 1048576, 2) . ' MB' : round($fileSize / 1024, 1) . ' KB';

    $host = getHostUrl();
    $shareUrl = $host . '/api/docu_ai.php?view=' . urlencode($docId);
    $downloadUrl = $host . '/api/docu_ai.php?download=' . urlencode($docId);
    $qrUrl = 'https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=' . urlencode($shareUrl);

    // Save to metadata registry
    $registry = [];
    if (file_exists($metaFile)) {
        $registry = json_decode(file_get_contents($metaFile), true) ?: [];
    }
    $registry[$docId] = [
        'doc_id' => $docId,
        'title' => $title,
        'file_name' => $fileName,
        'file_size' => $sizeFormatted,
        'pages_count' => $pagesCount,
        'uploaded_at' => time()
    ];
    @file_put_contents($metaFile, json_encode($registry, JSON_PRETTY_PRINT));

    echo json_encode([
        'success' => true,
        'doc_id' => $docId,
        'title' => $title,
        'share_url' => $shareUrl,
        'download_url' => $downloadUrl,
        'qr_url' => $qrUrl,
        'file_size_formatted' => $sizeFormatted,
        'pages_count' => $pagesCount
    ]);
    exit;
}

// List Cloud Documents
if ($action === 'cloud_list') {
    if (!isAuthorizedRequest($data)) {
        http_response_code(401);
        echo json_encode(['success' => false, 'error' => 'Unauthorized: Invalid access token']);
        exit;
    }
    $registry = [];
    if (file_exists($metaFile)) {
        $registry = json_decode(file_get_contents($metaFile), true) ?: [];
    }
    $host = getHostUrl();
    $list = [];
    foreach ($registry as $docId => $meta) {
        $meta['share_url'] = $host . '/api/docu_ai.php?view=' . urlencode($docId);
        $meta['download_url'] = $host . '/api/docu_ai.php?download=' . urlencode($docId);
        $list[] = $meta;
    }
    echo json_encode(['success' => true, 'documents' => array_reverse($list)]);
    exit;
}

// Delete Cloud Document
if ($action === 'cloud_delete') {
    if (!isAuthorizedRequest($data)) {
        http_response_code(401);
        echo json_encode(['success' => false, 'error' => 'Unauthorized: Invalid access token']);
        exit;
    }
    $docId = preg_replace('/[^a-zA-Z0-9_-]/', '', $data['doc_id'] ?? '');
    if (!empty($docId)) {
        $files = glob($storageDir . $docId . '.*');
        foreach ($files as $f) {
            @unlink($f);
        }
        if (file_exists($metaFile)) {
            $registry = json_decode(file_get_contents($metaFile), true) ?: [];
            unset($registry[$docId]);
            @file_put_contents($metaFile, json_encode($registry, JSON_PRETTY_PRINT));
        }
        echo json_encode(['success' => true, 'message' => 'Deleted document']);
        exit;
    }
    echo json_encode(['success' => false, 'error' => 'Missing doc_id']);
    exit;
}

// Fallback
echo json_encode(['success' => false, 'error' => 'Action not recognized']);
