package cn.iocoder.yudao.server.devtools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 后端启动就绪后，在桌面环境依次打开多个前端地址（便于联调）。
 * 通过 {@code yudao.devtools.browser.enabled=true} 开启；URL 列表见 {@code yudao.devtools.browser.urls}（英文逗号分隔）。
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "yudao.devtools.browser", name = "enabled", havingValue = "true")
public class DevBrowserLauncher implements ApplicationListener<ApplicationReadyEvent> {

    /**
     * 多个地址用英文逗号分隔，例如：管理端 + 聊天风险演示页。
     * 默认：admin-vue3（.env 常见 80）与 yudao-ui-chat-risk（vite 默认 5173）。
     */
    @Value("${yudao.devtools.browser.urls:http://127.0.0.1:80/,http://127.0.0.1:5173/}")
    private String browserUrlsRaw;

    @Value("${yudao.devtools.browser.delay-millis:1200}")
    private long delayMillis;

    /** 多窗口依次打开时的间隔，避免部分环境下第二个 tab 被吞 */
    @Value("${yudao.devtools.browser.stagger-millis:500}")
    private long staggerMillis;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        Thread t = new Thread(this::openInBackground, "yudao-dev-browser");
        t.setDaemon(true);
        t.start();
    }

    private void openInBackground() {
        try {
            if (delayMillis > 0) {
                Thread.sleep(delayMillis);
            }
            List<String> urls = resolveBrowserUrls();
            if (urls.isEmpty()) {
                log.warn("[DevBrowserLauncher] yudao.devtools.browser.urls 为空，跳过打开浏览器");
                return;
            }
            for (int i = 0; i < urls.size(); i++) {
                if (i > 0 && staggerMillis > 0) {
                    Thread.sleep(staggerMillis);
                }
                String url = urls.get(i);
                openBrowser(url);
                log.info("[DevBrowserLauncher] 已请求打开浏览器 ({}/{}): {}", i + 1, urls.size(), url);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("[DevBrowserLauncher] 已中断");
        } catch (Exception e) {
            log.warn("[DevBrowserLauncher] 打开浏览器失败（可忽略，或检查 yudao.devtools.browser.urls）: {}", e.getMessage());
        }
    }

    private List<String> resolveBrowserUrls() {
        List<String> out = new ArrayList<>();
        if (browserUrlsRaw == null) {
            return out;
        }
        for (String part : browserUrlsRaw.split(",")) {
            String u = part == null ? "" : part.trim();
            if (!u.isEmpty()) {
                out.add(u);
            }
        }
        return out;
    }

    private static void openBrowser(String url) throws Exception {
        if (!GraphicsEnvironment.isHeadless()
                && Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(new URI(url));
            return;
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            new ProcessBuilder("cmd", "/c", "start", "", url).start();
            return;
        }
        if (os.contains("mac")) {
            new ProcessBuilder("open", url).start();
            return;
        }
        new ProcessBuilder("xdg-open", url).start();
    }
}
