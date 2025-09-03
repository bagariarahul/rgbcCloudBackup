# CloudBackup Android App

A modern, modular Android app for scanning, indexing, and tracking backup status of user files. Built with Room, Hilt, Material 3, and Jetpack Compose.

---

## 🏗️ Project Structure

- **core/data/database/entity/FileIndex.kt**: Room entity for tracked files and backup states.
- **core/data/database/dao/FileIndexDao.kt**: Queries for file stat tracking, error/failed counts.
- **core/data/repository/FileRepositoryImpl.kt**: Implements business/data access logic.
- **core/domain/repository/FileRepository.kt**: Clean architecture interface for file stats, backup error, and failure queries.
- **core/domain/model/BackupStats.kt**: All backup statistics fields surfaced for UI/backend.
- **core/domain/usecase/GetBackupStatisticsUseCase.kt**: Orchestrates stat calculations for dashboards.
- **features/main/presentation/**: Compose UI ViewModel, screens, UI state, and component UIs.

---

## 🚩 TODOs and Incomplete Items

- [ ] **Room entity migrations** for new fields (`lastAttemptedAt`, `errorMessage`).
- [ ] **Unit and DAO tests** for error/failed backup queries.
- [ ] **Consistency check of `BackupStats`** model—ensure all usages include all parameters.
- [ ] **Hilt modules**: Provide all new/updated dependencies.
- [ ] **ProGuard rules**: Fix bad rules for Retrofit + Gson to avoid shrink/obfuscation crashes.
- [ ] **Resources**: Mirror all `_dark` color resources in `values/colors.xml` (not just `values-night`).
- [ ] **Code Cleanliness**: Remove deprecated/unused interface methods.
- [ ] **Docs**: Add KDoc/comments to complex queries and models.

---

## ✅ Setup Checklist

- [x] All Room entity/table fields finalized and present for all backup and error tracking.
- [x] Queries for file stats, cached and/or live via Flow, present in DAO.
- [x] Repository methods implemented for:
    - Get all files
    - Get files to backup
    - Mark file as backed up
    - Count total, backed up, failed, and error files
    - Sum total and backed up file sizes
- [x] Use-case class for gathering all statistics present and wired through DI.
- [x] UI state class exposes all statistics and error UI.
- [x] Composable UI with modern Compose idioms, error and loading surface, easy expansion.
- [x] All ProGuard rules for minification resolve without syntax errors.
- [x] Colors in `values/` and `values-night/` are mirrored for any `_dark` resource.

---

## 🧠 Advanced/Next Steps

- [ ] Cloud backup upload (remote APIs)
- [ ] Restore/download workflow
- [ ] Real user authentication
- [ ] Encrypted database and backup
- [ ] Nep/remote error logging for failed backup diagnostics
