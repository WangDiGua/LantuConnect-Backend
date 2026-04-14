package com.lantu.connect.common.captcha.impl;

import com.lantu.connect.common.captcha.CaptchaResult;
import com.lantu.connect.common.captcha.CaptchaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class SliderCaptchaServiceImpl implements CaptchaService {

    /**
     * 来自 {@code net.sourceforge.jeuclid:dejavu-fonts}，不依赖服务器安装 Arial/字体包。
     */
    private static final String CAPTCHA_FONT_RESOURCE = "fonts/DejaVuSans-Bold.ttf";

    private final StringRedisTemplate redisTemplate;
    private static final String CAPTCHA_PREFIX = "captcha:image:";
    private static final int EXPIRE_MINUTES = 5;
    private static final int CAPTCHA_WIDTH = 150;
    private static final int CAPTCHA_HEIGHT = 48;
    private static final int CAPTCHA_CODE_LENGTH = 5;
    private static final String CAPTCHA_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final int NOISE_LINE_COUNT = 8;
    private static final int NOISE_DOT_COUNT = 80;

    private final SecureRandom random = new SecureRandom();

    /** 一级缓存：TrueType 基字体，按字号 {@link Font#deriveFont(float)} */
    private volatile Font bundledFontBase;

    private Font bundledFontBase() {
        Font cached = bundledFontBase;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            cached = bundledFontBase;
            if (cached != null) {
                return cached;
            }
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            try (InputStream in = cl != null ? cl.getResourceAsStream(CAPTCHA_FONT_RESOURCE) : null) {
                if (in != null) {
                    cached = Font.createFont(Font.TRUETYPE_FONT, in);
                }
            } catch (FontFormatException | IOException e) {
                log.warn("Captcha: failed to load bundled font {}: {}", CAPTCHA_FONT_RESOURCE, e.getMessage());
            }
            if (cached == null) {
                cached = new Font(Font.SANS_SERIF, Font.BOLD, 12);
                log.debug("Captcha: using logical font SANS_SERIF (bundled TTF not available)");
            }
            bundledFontBase = cached;
            return cached;
        }
    }

    @Override
    public CaptchaResult generate() {
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        String captchaCode = generateCaptchaCode();
        String key = CAPTCHA_PREFIX + captchaId;
        redisTemplate.opsForValue().set(key, Objects.requireNonNull(captchaCode), EXPIRE_MINUTES, TimeUnit.MINUTES);

        CaptchaResult result = new CaptchaResult();
        result.setCaptchaId(captchaId);
        result.setCaptchaImage(generateCaptchaImage(captchaCode));

        log.debug("Generated captcha: id={}", captchaId);
        return result;
    }

    @Override
    public boolean verify(String captchaId, String code) {
        if (captchaId == null || code == null) {
            return false;
        }

        String key = CAPTCHA_PREFIX + captchaId;
        String storedCode = redisTemplate.opsForValue().get(key);

        if (storedCode == null) {
            log.warn("Captcha expired or not found: {}", captchaId);
            return false;
        }

        redisTemplate.delete(key);

        boolean valid = storedCode.equalsIgnoreCase(code.trim());
        log.debug("Captcha verification: id={}, valid={}", captchaId, valid);
        return valid;
    }

    private String generateCaptchaCode() {
        StringBuilder sb = new StringBuilder(CAPTCHA_CODE_LENGTH);
        for (int i = 0; i < CAPTCHA_CODE_LENGTH; i++) {
            sb.append(CAPTCHA_CHARS.charAt(random.nextInt(CAPTCHA_CHARS.length())));
        }
        return sb.toString();
    }

    private String generateCaptchaImage(String code) {
        BufferedImage image = new BufferedImage(CAPTCHA_WIDTH, CAPTCHA_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2d.setColor(randomColor(220, 250));
            g2d.fillRect(0, 0, CAPTCHA_WIDTH, CAPTCHA_HEIGHT);

            for (int i = 0; i < NOISE_LINE_COUNT; i++) {
                g2d.setColor(randomColor(120, 210));
                int x1 = random.nextInt(CAPTCHA_WIDTH);
                int y1 = random.nextInt(CAPTCHA_HEIGHT);
                int x2 = random.nextInt(CAPTCHA_WIDTH);
                int y2 = random.nextInt(CAPTCHA_HEIGHT);
                g2d.drawLine(x1, y1, x2, y2);
            }

            for (int i = 0; i < NOISE_DOT_COUNT; i++) {
                g2d.setColor(randomColor(100, 200));
                int x = random.nextInt(CAPTCHA_WIDTH);
                int y = random.nextInt(CAPTCHA_HEIGHT);
                g2d.fillRect(x, y, 1, 1);
            }

            int step = CAPTCHA_WIDTH / (CAPTCHA_CODE_LENGTH + 1);
            for (int i = 0; i < code.length(); i++) {
                char ch = code.charAt(i);
                int fontSize = 26 + random.nextInt(5);
                g2d.setFont(bundledFontBase().deriveFont((float) fontSize));
                g2d.setColor(randomColor(20, 120));

                int x = step * (i + 1) - 8 + random.nextInt(6);
                int y = CAPTCHA_HEIGHT - 10 + random.nextInt(5);
                double angle = (random.nextDouble() - 0.5D) * 0.6D;

                AffineTransform old = g2d.getTransform();
                g2d.rotate(angle, x, y);
                g2d.drawString(String.valueOf(ch), x, y);
                g2d.setTransform(old);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("生成验证码图片失败", e);
        } finally {
            g2d.dispose();
        }
    }

    private Color randomColor(int min, int max) {
        int r = min + random.nextInt(max - min + 1);
        int g = min + random.nextInt(max - min + 1);
        int b = min + random.nextInt(max - min + 1);
        return new Color(r, g, b);
    }
}
