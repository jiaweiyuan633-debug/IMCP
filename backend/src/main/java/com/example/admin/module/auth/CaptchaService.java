package com.example.admin.module.auth;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import com.example.admin.module.auth.vo.CaptchaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class CaptchaService {

    private static final String KEY_PREFIX = "captcha:";
    private static final String CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    private final StringRedisTemplate redisTemplate;

    public CaptchaResponse generate() throws Exception {
        String code = RandomUtil.randomString(CHARS, 4);
        BufferedImage image = new BufferedImage(120, 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 120, 40);
        graphics.setColor(Color.LIGHT_GRAY);
        for (int i = 0; i < 5; i++) {
            int x1 = RandomUtil.randomInt(0, 120);
            int y1 = RandomUtil.randomInt(0, 40);
            int x2 = RandomUtil.randomInt(0, 120);
            int y2 = RandomUtil.randomInt(0, 40);
            graphics.drawLine(x1, y1, x2, y2);
        }
        graphics.setFont(new Font("Arial", Font.BOLD, 24));
        for (int i = 0; i < code.length(); i++) {
            graphics.setColor(randomColor());
            graphics.drawString(String.valueOf(code.charAt(i)), 18 + i * 24, 29);
        }
        graphics.dispose();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        String captchaId = IdUtil.fastSimpleUUID();
        redisTemplate.opsForValue().set(KEY_PREFIX + captchaId, code, Duration.ofMinutes(5));
        return CaptchaResponse.builder()
                .captchaId(captchaId)
                .image("data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray()))
                .build();
    }

    public boolean verify(String captchaId, String code) {
        if (captchaId == null || code == null) {
            return false;
        }
        String key = KEY_PREFIX + captchaId;
        String saved = redisTemplate.opsForValue().get(key);
        if (saved == null || !saved.equalsIgnoreCase(code.trim())) {
            return false;
        }
        redisTemplate.delete(key);
        return true;
    }

    private Color randomColor() {
        return new Color(RandomUtil.randomInt(0, 120), RandomUtil.randomInt(0, 120), RandomUtil.randomInt(0, 120));
    }
}

