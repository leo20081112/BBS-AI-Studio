package mchorse.bbs_ai.debug;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.network.ClientPlayerEntity;

import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;

/**
 * 本地调试桥（供外部工具 / MCP 服务器访问游戏）
 *
 * <p>在 {@code 127.0.0.1} 上开一个极小的 HTTP 服务，仅本机可连，
 * 提供四个能力：截图（渲染线程抓帧）、执行聊天命令、发送聊天消息、
 * 读取日志尾部。用于自动化测试与远程诊断，不影响游戏正常功能。</p>
 *
 * <p>安全约束：
 * <ul>
 *   <li>只绑定回环地址，局域网内其他设备不可见</li>
 *   <li>首次启动时在 {@code config/bbs_ai_bridge.token} 生成随机令牌，
 *       所有请求需带 {@code Authorization: Bearer <令牌>} 头</li>
 *   <li>可用 JVM 参数 {@code -Dbbsai.bridge.port=0} 关闭</li>
 * </ul></p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class BBSAIDebugBridge
{
    /**
     * 单例
     */
    private static BBSAIDebugBridge instance;

    /**
     * 默认端口（可用 -Dbbsai.bridge.port 覆盖，0 = 关闭）
     */
    public static final int DEFAULT_PORT = 28080;

    /**
     * HTTP 服务实例（null = 未启动）
     */
    private HttpServer server;

    /**
     * 访问令牌（config/bbs_ai_bridge.token 内容）
     */
    private String token;

    /**
     * 获取单例
     */
    public static BBSAIDebugBridge get()
    {
        if (instance == null)
        {
            instance = new BBSAIDebugBridge();
        }

        return instance;
    }

    /**
     * 启动调试桥（端口解析自系统属性；失败仅告警，不影响游戏）
     */
    public void start()
    {
        int port = Integer.getInteger("bbsai.bridge.port", DEFAULT_PORT);

        if (port <= 0)
        {
            System.out.println("[BBS AI] 调试桥已通过 -Dbbsai.bridge.port=0 关闭");

            return;
        }

        try
        {
            this.token = this.loadOrCreateToken();

            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);

            server.createContext("/ping", this.guard(this::handlePing));
            server.createContext("/screenshot", this.guard(this::handleScreenshot));
            server.createContext("/command", this.guard(this::handleCommand));
            server.createContext("/chat", this.guard(this::handleChat));
            server.createContext("/log", this.guard(this::handleLog));
            server.createContext("/ui", this.guard(this::handleUi));
            server.setExecutor(Executors.newSingleThreadExecutor((r) ->
            {
                Thread thread = new Thread(r, "BBS AI 调试桥");

                thread.setDaemon(true);

                return thread;
            }));
            server.start();

            this.server = server;

            System.out.println("[BBS AI] 调试桥已启动：http://127.0.0.1:" + port + "（令牌见 config/bbs_ai_bridge.token）");
        }
        catch (Exception e)
        {
            System.err.println("[BBS AI] 调试桥启动失败（不影响游戏）：" + e);
        }
    }

    /**
     * 是否已启动
     */
    public boolean isRunning()
    {
        return this.server != null;
    }

    /**
     * 读取或生成访问令牌
     */
    private String loadOrCreateToken() throws IOException
    {
        File file = new File("config/bbs_ai_bridge.token");

        if (file.isFile())
        {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String token = new String(bytes, StandardCharsets.UTF_8).trim();

            if (!token.isEmpty())
            {
                return token;
            }
        }

        StringBuilder builder = new StringBuilder();
        SecureRandom random = new SecureRandom();

        for (int i = 0; i < 32; i++)
        {
            builder.append("0123456789abcdef".charAt(random.nextInt(16)));
        }

        String token = builder.toString();

        file.getParentFile().mkdirs();
        Files.write(file.toPath(), token.getBytes(StandardCharsets.UTF_8));

        return token;
    }

    /**
     * 包装处理器：统一令牌校验与异常兜底
     */
    private com.sun.net.httpserver.HttpHandler guard(com.sun.net.httpserver.HttpHandler handler)
    {
        return (exchange) ->
        {
            try
            {
                String auth = exchange.getRequestHeaders().getFirst("Authorization");
                String given = auth != null && auth.startsWith("Bearer ") ? auth.substring(7).trim() : "";

                if (!this.token.equals(given))
                {
                    this.sendJson(exchange, 401, "{\"ok\":false,\"error\":\"bad token\"}");

                    return;
                }

                handler.handle(exchange);
            }
            catch (Exception e)
            {
                try
                {
                    this.sendJson(exchange, 500, "{\"ok\":false,\"error\":" + quote(String.valueOf(e)) + "}");
                }
                catch (Exception ignored)
                {}
            }
            finally
            {
                exchange.close();
            }
        };
    }

    /**
     * GET /ping：存活与游戏状态快照
     */
    private void handlePing(HttpExchange exchange) throws IOException
    {
        MinecraftClient client = MinecraftClient.getInstance();
        StringBuilder json = new StringBuilder();

        json.append("{\"ok\":true,\"mod\":\"BBS AI Studio\"");
        json.append(",\"version\":").append(quote(modVersion()));
        json.append(",\"player\":").append(quote(client.getSession() == null ? "" : client.getSession().getUsername()));

        if (client.player != null)
        {
            ClientPlayerEntity player = client.player;

            json.append(",\"world\":\"in-game\"");
            json.append(",\"dimension\":").append(String.valueOf(client.world.getRegistryKey().getValue()));
            json.append(",\"pos\":[")
                .append(fmt(player.getX())).append(',')
                .append(fmt(player.getY())).append(',')
                .append(fmt(player.getZ())).append(']');
        }
        else
        {
            json.append(",\"world\":\"title\"");
        }

        json.append(",\"screen\":").append(quote(client.currentScreen == null ? null : client.currentScreen.getClass().getSimpleName()));
        json.append(",\"bridge\":true}");

        this.sendJson(exchange, 200, json.toString());
    }

    /**
     * GET /screenshot：在渲染线程抓取当前画面，返回 PNG
     */
    private void handleScreenshot(HttpExchange exchange) throws IOException
    {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()))
        {
            this.sendJson(exchange, 405, "{\"ok\":false,\"error\":\"GET only\"}");

            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        File output = File.createTempFile("bbsai-shot", ".png");
        CountDownLatch latch = new CountDownLatch(1);
        final Exception[] failure = { null };

        /* 截图必须发生在渲染线程（GL 上下文），投递到主线程执行并等待完成 */
        client.execute(() ->
        {
            try
            {
                /* takeScreenshot 内部已处理行序翻转，直接写出即可 */
                NativeImage image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer());

                image.writeTo(output);
                image.close();
            }
            catch (Exception e)
            {
                failure[0] = e;
            }
            finally
            {
                latch.countDown();
            }
        });

        try
        {
            latch.await(10, TimeUnit.SECONDS);
        }
        catch (InterruptedException ignored)
        {}

        if (failure[0] != null || !output.isFile())
        {
            this.sendJson(exchange, 500, "{\"ok\":false,\"error\":" + quote("screenshot failed: " + failure[0]) + "}");

            return;
        }

        exchange.getResponseHeaders().set("Content-Type", "image/png");
        exchange.sendResponseHeaders(200, output.length());

        try (OutputStream os = exchange.getResponseBody(); InputStream is = Files.newInputStream(output.toPath()))
        {
            is.transferTo(os);
        }

        output.delete();
    }

    /**
     * POST /command：{"command":"say hi"} 以玩家身份执行聊天命令
     */
    private void handleCommand(HttpExchange exchange) throws IOException
    {
        String body = this.readBody(exchange);
        String command = extractString(body, "command");
        MinecraftClient client = MinecraftClient.getInstance();

        if (command.isEmpty())
        {
            this.sendJson(exchange, 400, "{\"ok\":false,\"error\":\"command required\"}");

            return;
        }

        if (client.player == null || client.player.networkHandler == null)
        {
            this.sendJson(exchange, 409, "{\"ok\":false,\"error\":\"not in a world\"}");

            return;
        }

        final String trimmed = command.charAt(0) == '/' ? command.substring(1) : command;
        CountDownLatch latch = new CountDownLatch(1);

        client.execute(() ->
        {
            try
            {
                client.player.networkHandler.sendChatCommand(trimmed);
            }
            finally
            {
                latch.countDown();
            }
        });

        this.await(latch);
        this.sendJson(exchange, 200, "{\"ok\":true,\"sent\":true,\"command\":" + quote(trimmed) + "}");
    }

    /**
     * POST /chat：{"message":"hello"} 发送普通聊天消息
     */
    private void handleChat(HttpExchange exchange) throws IOException
    {
        String body = this.readBody(exchange);
        String message = extractString(body, "message");
        MinecraftClient client = MinecraftClient.getInstance();

        if (message.isEmpty())
        {
            this.sendJson(exchange, 400, "{\"ok\":false,\"error\":\"message required\"}");

            return;
        }

        if (client.player == null || client.player.networkHandler == null)
        {
            this.sendJson(exchange, 409, "{\"ok\":false,\"error\":\"not in a world\"}");

            return;
        }

        CountDownLatch latch = new CountDownLatch(1);

        client.execute(() ->
        {
            try
            {
                client.player.networkHandler.sendChatMessage(message);
            }
            finally
            {
                latch.countDown();
            }
        });

        this.await(latch);
        this.sendJson(exchange, 200, "{\"ok\":true,\"sent\":true}");
    }

    /**
     * GET /log?lines=200：返回 logs/latest.log 尾部（纯文本）
     */
    private void handleLog(HttpExchange exchange) throws IOException
    {
        int lines = 200;

        try
        {
            String query = exchange.getRequestURI().getQuery();

            if (query != null && query.contains("lines="))
            {
                lines = Math.max(1, Math.min(5000, Integer.parseInt(query.replaceAll(".*lines=(\\d+).*", "$1"))));
            }
        }
        catch (Exception ignored)
        {}

        File log = new File("logs/latest.log");
        StringBuilder builder = new StringBuilder();

        if (log.isFile())
        {
            /* 只读尾部：日志可能很大，从文件末尾向前找 N 个换行 */
            try (RandomAccessFile raf = new RandomAccessFile(log, "r"))
            {
                long size = raf.length();
                long start = Math.max(0, size - lines * 256L);
                byte[] chunk = new byte[(int) (size - start)];

                raf.seek(start);
                raf.readFully(chunk);

                String text = new String(chunk, StandardCharsets.UTF_8);
                String[] all = text.split("\r?\n");
                int from = Math.max(0, all.length - lines);

                for (int i = from; i < all.length; i++)
                {
                    builder.append(all[i]).append('\n');
                }
            }
        }
        else
        {
            builder.append("(logs/latest.log 不存在)");
        }

        byte[] bytes = builder.toString().getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);

        try (OutputStream os = exchange.getResponseBody())
        {
            os.write(bytes);
        }
    }

    /**
     * POST /ui：程序化打开界面，供自动化测试免点击导航
     *
     * <p>支持字段（可组合，按序执行）：{@code close=true} 关闭当前界面；
     * {@code panel="ai_tools"} 打开 AI 工具面板（可带 {@code section}）；
     * {@code panel="ai_editor"} 打开 AI 编辑器面板；{@code panel="ai_settings"} 打开 AI 设置界面；
     * {@code guide=true} 弹出首次引导；{@code settings=true} 弹出 BBS 设置。</p>
     */
    private void handleUi(HttpExchange exchange) throws IOException
    {
        final String body = this.readBody(exchange);
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.player == null)
        {
            this.sendJson(exchange, 409, "{\"ok\":false,\"error\":\"not in a world\"}");

            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        final String[] error = { null };

        client.execute(() ->
        {
            try
            {
                if (extractBool(body, "close"))
                {
                    client.setScreen(null);
                }

                String panelName = extractString(body, "panel");

                if ("film".equals(panelName) || "model_editor".equals(panelName) || "morphing".equals(panelName))
                {
                    mchorse.bbs_mod.ui.dashboard.UIDashboard dashboard = mchorse.bbs_mod.BBSModClient.getDashboard();
                    UIDashboardPanel target = "film".equals(panelName)
                        ? dashboard.getPanel(mchorse.bbs_mod.ui.film.UIFilmPanel.class)
                        : "model_editor".equals(panelName)
                            ? dashboard.getPanel(mchorse.bbs_mod.ui.model_editor.UIModelEditorPanel.class)
                            : dashboard.getPanel(mchorse.bbs_mod.ui.morphing.UIMorphingPanel.class);

                    if (target == null)
                    {
                        error[0] = "panel not found: " + panelName;

                        return;
                    }

                    dashboard.setPanel(target);
                    mchorse.bbs_mod.ui.framework.UIScreen.open(dashboard);
                }

                if ("ik_panel".equals(extractString(body, "panel")))
                {
                    mchorse.bbs_mod.ui.dashboard.UIDashboard dashboard = mchorse.bbs_mod.BBSModClient.getDashboard();

                    mchorse.bbs_ai.ik.IKBoneChain chain = new mchorse.bbs_ai.ik.IKBoneChain("ik_check");
                    mchorse.bbs_ai.ik.IKBone bone = new mchorse.bbs_ai.ik.IKBone("head");

                    bone.setPositions(new org.joml.Vector3f(0.0F, 1.4F, 0.0F), new org.joml.Vector3f(0.0F, 1.9F, 0.0F));
                    chain.addBone(bone);

                    mchorse.bbs_mod.ui.framework.UIScreen.open(dashboard);
                    mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay.addOverlay(
                        dashboard.context,
                        new mchorse.bbs_ai.ik.BlenderIKSettingsPanel(
                            new mchorse.bbs_ai.ik.BlenderIKComponent("ik_check", chain),
                            () ->
                            {}),
                        320,
                        640
                    );
                }

                if ("ai_tools".equals(extractString(body, "panel")))
                {
                    String section = extractString(body, "section");
                    mchorse.bbs_mod.ui.dashboard.UIDashboard dashboard = mchorse.bbs_mod.BBSModClient.getDashboard();
                    mchorse.bbs_ai.ui.panel.UIAIToolsPanel panel = dashboard.getPanel(mchorse.bbs_ai.ui.panel.UIAIToolsPanel.class);

                    if (panel == null)
                    {
                        error[0] = "ai tools panel not found";

                        return;
                    }

                    if (!section.isEmpty())
                    {
                        panel.showSection(section);
                    }

                    /* bbs-fs 2.6 面板即标签页：先切换再打开屏幕，否则停留在原面板 */
                    dashboard.setPanel(panel);
                    mchorse.bbs_mod.ui.framework.UIScreen.open(dashboard);
                }

                if ("ai_editor".equals(extractString(body, "panel")))
                {
                    mchorse.bbs_mod.ui.dashboard.UIDashboard dashboard = mchorse.bbs_mod.BBSModClient.getDashboard();
                    mchorse.bbs_ai.ui.editor.UIAIEditorPanel panel = dashboard.getPanel(mchorse.bbs_ai.ui.editor.UIAIEditorPanel.class);

                    if (panel == null)
                    {
                        error[0] = "ai editor panel not found";

                        return;
                    }

                    if (extractBool(body, "demo"))
                    {
                        panel.seedDemoForTesting();
                    }

                    dashboard.setPanel(panel);
                    mchorse.bbs_mod.ui.framework.UIScreen.open(dashboard);
                }

                if ("ai_settings".equals(extractString(body, "panel")))
                {
                    mchorse.bbs_mod.ui.dashboard.UIDashboard dashboard = mchorse.bbs_mod.BBSModClient.getDashboard();

                    mchorse.bbs_mod.ui.framework.UIScreen.open(dashboard);
                    mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay.addOverlay(
                        dashboard.context,
                        new mchorse.bbs_ai.ui.editor.AISettingsOverlayPanel(),
                        420,
                        440
                    );
                }

                if (extractBool(body, "guide"))
                {
                    mchorse.bbs_mod.ui.dashboard.UIDashboard dashboard = mchorse.bbs_mod.BBSModClient.getDashboard();

                    mchorse.bbs_mod.ui.framework.UIScreen.open(dashboard);
                    mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay.addOverlay(dashboard.context, new mchorse.bbs_ai.ui.hotkey.FirstTimeGuide());
                }

                if (extractBool(body, "settings"))
                {
                    mchorse.bbs_mod.ui.dashboard.UIDashboard dashboard = mchorse.bbs_mod.BBSModClient.getDashboard();

                    mchorse.bbs_mod.ui.framework.UIScreen.open(dashboard);
                    mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay.addOverlay(dashboard.context, new mchorse.bbs_mod.settings.ui.UISettingsOverlayPanel());
                }
            }
            catch (Exception e)
            {
                error[0] = String.valueOf(e);
            }
            finally
            {
                latch.countDown();
            }
        });

        this.await(latch);

        if (error[0] != null)
        {
            this.sendJson(exchange, 500, "{\"ok\":false,\"error\":" + quote(error[0]) + "}");

            return;
        }

        this.sendJson(exchange, 200, "{\"ok\":true}");
    }

    /**
     * 读取请求体（UTF-8，上限 64 KB）
     */
    private String readBody(HttpExchange exchange) throws IOException
    {
        byte[] bytes = exchange.getRequestBody().readNBytes(64 * 1024);

        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 等待主线程任务完成
     */
    private void await(CountDownLatch latch)
    {
        try
        {
            latch.await(5, TimeUnit.SECONDS);
        }
        catch (InterruptedException ignored)
        {}
    }

    /**
     * 发送 JSON 响应
     */
    private void sendJson(HttpExchange exchange, int status, String json) throws IOException
    {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);

        try (OutputStream os = exchange.getResponseBody())
        {
            os.write(bytes);
        }
    }

    /**
     * 极简 JSON 字符串转义
     */
    private static String quote(String value)
    {
        if (value == null)
        {
            return "null";
        }

        StringBuilder builder = new StringBuilder("\"");

        for (int i = 0; i < value.length(); i++)
        {
            char c = value.charAt(i);

            if (c == '"' || c == '\\')
            {
                builder.append('\\').append(c);
            }
            else if (c < 0x20)
            {
                builder.append(String.format("\\u%04x", (int) c));
            }
            else
            {
                builder.append(c);
            }
        }

        return builder.append('"').toString();
    }

    /**
     * 从手写 JSON 体中取布尔字段（true 字面量）
     */
    private static boolean extractBool(String body, String field)
    {
        if (body == null)
        {
            return false;
        }

        String key = "\"" + field + "\"";
        int keyIndex = body.indexOf(key);

        if (keyIndex < 0)
        {
            return false;
        }

        int colon = body.indexOf(':', keyIndex + key.length());

        if (colon < 0)
        {
            return false;
        }

        /* 跳过冒号后的空白（json.dumps 默认输出 ": true"） */
        int cursor = colon + 1;

        while (cursor < body.length() && Character.isWhitespace(body.charAt(cursor)))
        {
            cursor++;
        }

        return body.startsWith("true", cursor);
    }

    /**
     * 从手写 JSON 体中取字符串字段（避免引依赖）
     */
    private static String extractString(String body, String field)
    {
        if (body == null)
        {
            return "";
        }

        String key = "\"" + field + "\"";
        int keyIndex = body.indexOf(key);

        if (keyIndex < 0)
        {
            return "";
        }

        int colon = body.indexOf(':', keyIndex + key.length());

        if (colon < 0)
        {
            return "";
        }

        int start = body.indexOf('"', colon);

        if (start < 0)
        {
            return "";
        }

        StringBuilder builder = new StringBuilder();

        for (int i = start + 1; i < body.length(); i++)
        {
            char c = body.charAt(i);

            if (c == '\\' && i + 1 < body.length())
            {
                char next = body.charAt(++i);

                builder.append(next == 'n' ? '\n' : next == 't' ? '\t' : next);
            }
            else if (c == '"')
            {
                break;
            }
            else
            {
                builder.append(c);
            }
        }

        return builder.toString();
    }

    /**
     * 坐标格式化（保留 1 位小数）
     */
    private static String fmt(double value)
    {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    /**
     * 模组版本（来自 fabric.mod.json，读取失败回退 unknown）
     */
    private static String modVersion()
    {
        try
        {
            return FabricLoader.getInstance()
                .getModContainer(BBSMod.MOD_ID)
                .map((container) -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        }
        catch (Exception e)
        {
            return "unknown";
        }
    }
}
