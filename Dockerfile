# ===== 多阶段构建：主链路默认走 fingerprint-chromium =====

# 阶段1: 构建前端
FROM node:20-alpine AS frontend-build

WORKDIR /app/vue-code

RUN npm config set registry https://registry.npmmirror.com

COPY vue-code/package.json vue-code/package-lock.json ./
RUN npm ci

COPY vue-code/ ./
RUN npm run build:spring

# 阶段2: 构建后端 JAR
FROM eclipse-temurin:21-jdk-alpine AS backend-build

WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw mvnw.cmd pom.xml ./
RUN chmod +x mvnw

COPY src/ src/
COPY --from=frontend-build /app/vue-code/../src/main/resources/static src/main/resources/static/

RUN ./mvnw clean package -DskipTests

# 阶段3: 下载并解压 fingerprint-chromium AppImage
# 解压到 /opt/fingerprint-chromium，避免运行时还需要 FUSE
FROM debian:bookworm-slim AS chromium-extract

ARG FINGERPRINT_CHROMIUM_URL=https://github.com/adryfish/fingerprint-chromium/releases/download/144.0.7559.132/ungoogled-chromium-144.0.7559.132-1-x86_64.AppImage

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /tmp
RUN curl -L --fail --retry 3 -o chromium.AppImage "$FINGERPRINT_CHROMIUM_URL" \
    && chmod +x chromium.AppImage \
    && ./chromium.AppImage --appimage-extract > /dev/null \
    && mv squashfs-root /opt/fingerprint-chromium \
    && rm chromium.AppImage \
    && CHROME_BIN=$(find /opt/fingerprint-chromium -maxdepth 4 -type f \( -name 'chrome' -o -name 'chromium' -o -name 'chrome-bin' \) -executable | head -n1) \
    && if [ -z "$CHROME_BIN" ]; then echo "未在 AppImage 解压目录中找到 chrome 可执行文件" >&2; ls -la /opt/fingerprint-chromium >&2; exit 1; fi \
    && ln -sf "$CHROME_BIN" /opt/fingerprint-chromium/chrome-launcher \
    && echo "fingerprint-chromium 入口已链接: $CHROME_BIN -> /opt/fingerprint-chromium/chrome-launcher"

# 阶段4: 运行时镜像 (Ubuntu 22.04 jammy，兼容 AppImage 解压出的 GLIBC 二进制)
FROM eclipse-temurin:21-jre-jammy

LABEL maintainer="Ran"
LABEL description="XianYuAssistant - 主链路默认走 fingerprint-chromium"

WORKDIR /app

RUN mkdir -p /app/dbdata/captcha-debug /app/logs /app/browser_data

# Chromium 运行时依赖 + Xvfb + 中文字体 + locale
RUN apt-get update && apt-get install -y --no-install-recommends \
        xvfb \
        dbus \
        ca-certificates \
        tzdata \
        locales \
        curl \
        libnss3 \
        libnspr4 \
        libdbus-1-3 \
        libgbm1 \
        libdrm2 \
        libxkbcommon0 \
        libxcomposite1 \
        libxdamage1 \
        libxfixes3 \
        libxrandr2 \
        libxext6 \
        libx11-6 \
        libx11-xcb1 \
        libxcb1 \
        libpangocairo-1.0-0 \
        libpango-1.0-0 \
        libcairo2 \
        libcups2 \
        libatk1.0-0 \
        libatk-bridge2.0-0 \
        libatspi2.0-0 \
        libasound2 \
        libsecret-1-0 \
        fonts-noto-cjk \
        fonts-noto-color-emoji \
        fonts-liberation \
    && sed -i 's/# zh_CN.UTF-8 UTF-8/zh_CN.UTF-8 UTF-8/' /etc/locale.gen \
    && locale-gen \
    && rm -rf /var/lib/apt/lists/*

# 复制 fingerprint-chromium 解压目录
COPY --from=chromium-extract /opt/fingerprint-chromium /opt/fingerprint-chromium

# 复制后端 JAR
COPY --from=backend-build /app/target/XianYuAssistant-1.1.4.jar app.jar

EXPOSE 12400

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENV SERVER_PORT=12400
ENV ALI_API_KEY=""

# AppImage 解压后 chrome 二进制依赖同目录及 usr/lib 下的 .so，需要 LD_LIBRARY_PATH
ENV LD_LIBRARY_PATH=/opt/fingerprint-chromium:/opt/fingerprint-chromium/usr/lib
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
ENV DISPLAY=:99
ENV DBUS_SESSION_BUS_ADDRESS=/dev/null
ENV LANG=zh_CN.UTF-8
ENV LC_ALL=zh_CN.UTF-8

# 启动脚本（与原 Dockerfile 共用）
COPY docker-entrypoint.sh /app/
RUN sed -i 's/\r$//' /app/docker-entrypoint.sh && chmod +x /app/docker-entrypoint.sh

ENTRYPOINT ["/app/docker-entrypoint.sh"]
