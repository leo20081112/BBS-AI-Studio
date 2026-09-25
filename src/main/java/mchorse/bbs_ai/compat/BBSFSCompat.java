package mchorse.bbs_ai.compat;

import net.fabricmc.loader.api.FabricLoader;

/**
 * bbs-fs 版本兼容层（编译转换 + 运行时探测）
 *
 * <p>同一份 bbs_ai 源码需要兼容 2.5 / 2.6 / 2.7 三代上游：
 * <ul>
 *   <li><b>编译期</b>：2.7-only API（TimelineEvents/FilmEditEvents/FilmTools/
 *       ReplayActions/FormPoseEvents）在 2.5/2.6 树上不存在——对这些 API 的引用
 *       必须走 {@code AIReflect27} 反射封装或隔离在 2.7 分支独有文件中</li>
 *   <li><b>运行期</b>：本类解析 bbs 容器版本号，提供 {@link #isAtLeast} 探测，
 *       供功能开关守卫（如时间轴覆盖层仅在 2.7+ 注册）</li>
 * </ul></p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class BBSFSCompat
{
    /**
     * 上游 bbs-fs 版本（major/minor；解析失败时按 2.6 处理）
     */
    public static final int MAJOR;
    public static final int MINOR;

    public static final boolean HAS_27_API;

    static
    {
        int major = 2;
        int minor = 6;

        try
        {
            String version = FabricLoader.getInstance()
                .getModContainer("bbs")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("2.6");

            String[] parts = version.split("\\.");

            if (parts.length >= 2)
            {
                major = Integer.parseInt(parts[0].replaceAll("[^0-9]", ""));
                minor = Integer.parseInt(parts[1].replaceAll("[^0-9]", "").trim());
            }
        }
        catch (Exception e)
        {
            System.err.println("[BBS AI] bbs 版本解析失败，按 2.6 处理：" + e);
        }

        MAJOR = major;
        MINOR = minor;
        HAS_27_API = MAJOR > 2 || MINOR >= 7;
    }

    /**
     * 上游版本是否不低于给定版本
     */
    public static boolean isAtLeast(int major, int minor)
    {
        return MAJOR > major || (MAJOR == major && MINOR >= minor);
    }

    /**
     * 2.7 新 addon 事件体系是否可用（时间轴覆盖层/影片工具/角色属性控件/姿势钩子）
     */
    public static boolean has27Api()
    {
        return HAS_27_API;
    }
}
