# ECS Update Deploy Plan

## Goal

Update the existing Aliyun ECS deployment of `java-study` with the current workspace changes, including the Java Learning logo and demo login improvements.

## Target

- Project directory: `/www/wwwroot/java-study`
- Public URL: `http://47.93.15.207:8083`
- Backend URL: `http://127.0.0.1:18080`

## Tasks

- [x] Inspect current remote deployment state.
- [x] Run local verification before packaging.
- [x] Package and upload the current workspace.
- [x] Build frontend and backend on ECS.
- [x] Restart backend service and reload Nginx if needed.
- [x] Verify public page, logo asset, health API, and demo login.
