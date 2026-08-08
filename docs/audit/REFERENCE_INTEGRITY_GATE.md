# KSH workflow-audit reference integrity gate

Gate này kiểm bốn lớp tham chiếu trong toàn bộ `docs/audit/**/*.md`: local Markdown link, đường dẫn source bắt đầu bằng `src/` hoặc `scripts/`, tên file source đứng độc lập trong inline code, và line/range không vượt EOF. Nó phân biệt rõ **broken documentation reference** với lỗi runtime: file/link sai làm gate tài liệu fail, nhưng chỉ source call/render/import thật tới tài nguyên sai mới là code defect.

| Inventory | Checked | Missing |
|---|---:|---:|
| Audit Markdown files | 48 | 0 |
| Local Markdown links | 3106 | 0 |
| Rooted source paths | 6905 | 0 |
| Inline source basenames | 768 | 0 |
| Source line/range references | 3203 | 0 |

## Status

**PASS** — không có local link/rooted source path/inline source basename nào trỏ tới file không tồn tại, và không có line/range nào vượt EOF.
