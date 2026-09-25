/**
 * 预烘焙预览系统（数据层）
 * 
 * <p>所有 AI 生成结果先进预览、经用户确认才写入正式 Film【原版兼容】：
 * {@link mchorse.bbs_ai.preview.PreviewSystem} 总控（stage/bake/discard）、
 * {@link mchorse.bbs_ai.preview.PrebakeCache} 内存缓存、
 * {@link mchorse.bbs_ai.preview.BakeTarget} 可插拔写入目标（客户端实时编辑流 / FilmManager 离线流）。
 * 渲染与确认对话框在 client 源集。</p>
 */
package mchorse.bbs_ai.preview;
