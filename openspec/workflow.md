# 维护功能 Spec

阅读项目直接打开 [功能 Spec](README.md)，按业务查看。每份 Spec 的「当前实现」维护接口、处理步骤、源码与现实边界；`Requirements` 保留稳定编号及可验收场景。

1. **文档整理。** 在相应 `specs/<功能>/spec.md` 更新说明，保留原行为要求。规则和接口实际变更时再写差异。
2. **行为变更。** 在 `changes/<变更>/` 记录问题、差异规格、必要设计、任务与验证。MODIFIED 使用原完整标题并保留其余场景。验证与实现完成后合入对应功能 Spec，再归档。
3. **验证。** 维护 [验证入口](verification-matrix.md)，记录实际测试条件和结果。未完成项留在变更目录等待验证。

```bash
npx --yes @fission-ai/openspec@2.0.0 validate --all --strict --no-interactive
npx --yes @fission-ai/openspec@2.0.0 archive <变更> --yes
```

维护参考 [项目约定](project.md) · [模板](templates/) · [变更记录](changes/)
