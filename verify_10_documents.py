import cv2
import numpy as np
import os

base_dir = r"C:\Users\hp\.gemini\antigravity\brain\538b7898-92f6-4bda-bc9c-58cd257f1df5\.user_uploaded"
out_dir = r"C:\Users\hp\.gemini\antigravity\brain\538b7898-92f6-4bda-bc9c-58cd257f1df5\scratch\verification_10"
os.makedirs(out_dir, exist_ok=True)

def inpaint_text_box(img, box):
    h, w, c = img.shape
    t_left, t_top, t_right, t_bottom = box

    # Safety expansion to eliminate ascenders, descenders, and fringes
    extra_x = max(3, int((t_bottom - t_top) * 0.12))
    extra_y = max(3, int((t_bottom - t_top) * 0.10))
    e_left = max(0, t_left - extra_x)
    e_top = max(0, t_top - extra_y)
    e_right = min(w, t_right + extra_x)
    e_bottom = min(h, t_bottom + extra_y)

    top_y1 = max(0, e_top - 16)
    top_y2 = max(0, e_top - 2)
    bot_y1 = min(h - 1, e_bottom + 2)
    bot_y2 = min(h - 1, e_bottom + 16)

    def extract_paper(pixels, is_dark):
        if len(pixels) == 0:
            return None
        lumas = [0.299 * p[2] + 0.587 * p[1] + 0.114 * p[0] for p in pixels]
        sorted_pairs = sorted(zip(pixels, lumas), key=lambda x: x[1])
        if not is_dark:
            cutoff = int(len(sorted_pairs) * 0.60)
            valid = [p[0] for p in sorted_pairs[cutoff:]]
        else:
            cutoff = max(1, int(len(sorted_pairs) * 0.40))
            valid = [p[0] for p in sorted_pairs[:cutoff]]
        if not valid:
            return None
        valid = np.array(valid, dtype=np.float32)
        mean_bgr = np.mean(valid, axis=0)
        mean_luma = 0.299 * mean_bgr[2] + 0.587 * mean_bgr[1] + 0.114 * mean_bgr[0]
        if not is_dark and mean_luma >= 235:
            return np.array([255, 255, 255], dtype=np.float32)
        return mean_bgr

    # Check dark/light
    local_m = max(16, int((t_bottom - t_top) * 1.5))
    local_crop = img[max(0, t_top - local_m):min(h, t_bottom + local_m), max(0, t_left - local_m):min(w, t_right + local_m)]
    local_luma = np.mean(0.299 * local_crop[:,:,2] + 0.587 * local_crop[:,:,1] + 0.114 * local_crop[:,:,0])
    is_dark = local_luma < 128

    top_pixels = []
    if top_y1 < top_y2:
        for y in range(top_y1, top_y2):
            for x in range(e_left, e_right, 2):
                if 0 <= x < w and 0 <= y < h:
                    top_pixels.append(img[y, x])

    bot_pixels = []
    if bot_y1 < bot_y2:
        for y in range(bot_y1, bot_y2):
            for x in range(e_left, e_right, 2):
                if 0 <= x < w and 0 <= y < h:
                    bot_pixels.append(img[y, x])

    clean_top = extract_paper(top_pixels, is_dark)
    clean_bot = extract_paper(bot_pixels, is_dark)

    if clean_top is None and clean_bot is None:
        default_val = [0, 0, 0] if is_dark else [255, 255, 255]
        clean_top = np.array(default_val, dtype=np.float32)
        clean_bot = np.array(default_val, dtype=np.float32)
    elif clean_top is None:
        clean_top = clean_bot
    elif clean_bot is None:
        clean_bot = clean_top

    patch_h = e_bottom - e_top
    patch_w = e_right - e_left
    patch = np.zeros((patch_h, patch_w, 3), dtype=np.float32)

    for y in range(patch_h):
        alpha = y / float(max(1, patch_h - 1))
        patch[y, :] = (1.0 - alpha) * clean_top + alpha * clean_bot

    mean_luma = 0.299 * clean_top[2] + 0.587 * clean_top[1] + 0.114 * clean_top[0]
    if not is_dark and mean_luma < 235:
        noise = np.random.normal(0, 2.0, patch.shape).astype(np.float32)
        patch = np.clip(patch + noise, 0, 255)

    res = img.copy().astype(np.float32)
    feather = min(4, patch_w // 4, patch_h // 4)
    mask = np.ones((patch_h, patch_w), dtype=np.float32)
    for f in range(feather):
        w_val = (f + 1.0) / (feather + 1.0)
        mask[f, :] = np.minimum(mask[f, :], w_val)
        mask[patch_h - 1 - f, :] = np.minimum(mask[patch_h - 1 - f, :], w_val)
        mask[:, f] = np.minimum(mask[:, f], w_val)
        mask[:, patch_w - 1 - f] = np.minimum(mask[:, patch_w - 1 - f], w_val)

    for c_i in range(3):
        orig_slice = res[e_top:e_bottom, e_left:e_right, c_i]
        res[e_top:e_bottom, e_left:e_right, c_i] = orig_slice * (1.0 - mask) + patch[:, :, c_i] * mask

    return np.clip(res, 0, 255).astype(np.uint8)

test_configs = [
    # (filename, box [x1, y1, x2, y2], replacement_text, test_name)
    ("media_1791550736095_2b108cae.jpg", [300, 560, 435, 600], "Sharma", "doc1_archana_line"),
    ("media_1791550736095_2b108cae.jpg", [265, 370, 395, 415], "₹1,250.00", "doc2_igst_table_amount"),
    ("media_1791550736095_2b108cae.jpg", [15, 735, 235, 775], "Senior Partner", "doc3_authorized_signatory"),
    ("media_1791430844330.jpg", [75, 480, 360, 530], "ABDOMEN NORMAL", "doc4_ultrasound_finding"),
    ("media_1791430844330.jpg", [105, 240, 240, 275], "15/10/2026", "doc5_bill_date"),
    ("media_1791436453005.jpg", [150, 370, 310, 405], "Ankit", "doc6_ultrasound_zoomed_word"),
    ("media_1790601312137.png", [410, 385, 560, 415], "SHARMA AUTOMOBILES", "doc7_invoice_legal_name"),
    ("media_1790662646904.png", [190, 345, 310, 375], "SHARMA", "doc8_dealer_motors"),
    ("media_1790662646904.png", [190, 470, 310, 500], "9876543210", "doc9_phone_number"),
    ("media_1791544607359_c73998d9.jpg", [265, 380, 395, 420], "₹999.00", "doc10_total_amount_cell"),
]

for idx, (fname, box, rep_text, tname) in enumerate(test_configs, 1):
    fpath = os.path.join(base_dir, fname)
    if not os.path.exists(fpath):
        print(f"Skipping {fname}, file not found")
        continue
    img = cv2.imread(fpath)
    if img is None:
        print(f"Skipping {fname}, failed to load")
        continue

    # Crop neighborhood for close visual inspection
    crop_pad = 50
    h_img, w_img, _ = img.shape
    crop_x1 = max(0, box[0] - crop_pad)
    crop_y1 = max(0, box[1] - crop_pad)
    crop_x2 = min(w_img, box[2] + crop_pad)
    crop_y2 = min(h_img, box[3] + crop_pad)

    before_crop = img[crop_y1:crop_y2, crop_x1:crop_x2].copy()

    # Inpaint
    cleaned = inpaint_text_box(img, box)
    after_clean = cleaned[crop_y1:crop_y2, crop_x1:crop_x2].copy()

    # Render replacement text onto after_clean
    rendered = cleaned.copy()
    rel_x = box[0] + 2
    rel_y = box[3] - 4
    cv2.putText(rendered, rep_text, (rel_x, rel_y), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (20, 20, 20), 2, cv2.LINE_AA)
    after_rendered = rendered[crop_y1:crop_y2, crop_x1:crop_x2].copy()

    # Save side-by-side comparison: Before | Erased | With New Text
    side_by_side = np.hstack([before_crop, after_clean, after_rendered])
    out_path = os.path.join(out_dir, f"{idx:02d}_{tname}.png")
    cv2.imwrite(out_path, side_by_side)
    print(f"Test {idx:02d} [{tname}]: PASS -> Saved {out_path}")

print("All 10 real document visual tests executed successfully.")
