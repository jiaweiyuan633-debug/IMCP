// 默认走同源 /api（由 nginx/Ingress 反代到后端，与 frontend/Dockerfile 注释一致）；
// 独立部署或本地联调时通过 VITE_API_BASE_URL 注入绝对地址（如 http://localhost:8080/api）
export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')

