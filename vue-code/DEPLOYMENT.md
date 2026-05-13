# Vue 项目部署到 Spring Boot

## 方式一：自动构建脚本（推荐）

直接运行构建脚本：

```bash
# Windows
cd vue-code
build-to-spring.bat

# Linux/Mac
cd vue-code
chmod +x build-to-spring.sh
./build-to-spring.sh
```

## 方式二：手动构建

### 1. 构建 Vue 项目

```bash
cd vue-code
npm run build
```

构建完成后，文件会自动输出到 `src/main/resources/static/` 目录。

### 2. 启动 Spring Boot

```bash
cd ..
mvn spring-boot:run
```

或者使用 IDE 直接运行 Spring Boot 应用。

### 3. 访问应用

打开浏览器访问：`http://localhost:8080`

## 配置说明

### Vue 配置 (vite.config.ts)

```typescript
build: {
  outDir: '../src/main/resources/static',  // 输出到 Spring Boot static 目录
  assetsDir: 'assets',
  sourcemap: false,
  emptyOutDir: true  // 构建前清空目录
}
```

### Spring Boot 配置

Spring Boot 会自动服务 `src/main/resources/static/` 目录下的静态文件。

默认情况下：
- 访问 `http://localhost:8080/` 会自动加载 `index.html`
- API 请求路径为 `http://localhost:8080/api/*`

## 开发模式 vs 生产模式

### 开发模式（推荐用于开发）

前后端分离运行：

1. 启动 Spring Boot（端口 8080）
2. 启动 Vue 开发服务器（端口 5173）
   ```bash
   cd vue-code
   npm run dev
   ```
3. 访问 `http://localhost:5173`

优点：
- 热重载，修改代码立即生效
- 开发体验更好
- 前后端独立调试

### 生产模式（用于部署）

前后端一体化运行：

1. 构建 Vue 项目到 static 目录
2. 启动 Spring Boot
3. 访问 `http://localhost:8080`

优点：
- 只需要一个端口
- 部署简单
- 适合生产环境

## 注意事项

1. **API 路径**：Vue 项目中的 API 请求都使用 `/api` 前缀，确保与后端路由匹配

2. **路由模式**：Vue Router 使用 `createWebHistory` 模式，需要后端配置支持 SPA 路由

3. **构建前清理**：每次构建会清空 static 目录，确保没有旧文件残留

4. **CORS 配置**：生产模式下不需要 CORS 配置，因为前后端同源

## 故障排查

### 问题：访问 http://localhost:8080 显示 404

**解决方案**：
1. 确认 `src/main/resources/static/index.html` 文件存在
2. 重新构建 Vue 项目
3. 重启 Spring Boot 应用

### 问题：API 请求失败

**解决方案**：
1. 检查 API 路径是否正确（应该是 `/api/*`）
2. 查看浏览器控制台的网络请求
3. 检查 Spring Boot 后端是否正常运行

### 问题：页面刷新后 404

**解决方案**：
需要配置 Spring Boot 支持 SPA 路由，添加以下配置类：

```java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/{spring:\\w+}")
                .setViewName("forward:/index.html");
        registry.addViewController("/**/{spring:\\w+}")
                .setViewName("forward:/index.html");
    }
}
```

## 持续集成

可以在 Maven 构建过程中自动构建前端：

```xml
<plugin>
    <groupId>com.github.eirslett</groupId>
    <artifactId>frontend-maven-plugin</artifactId>
    <version>1.12.1</version>
    <configuration>
        <workingDirectory>vue-code</workingDirectory>
    </configuration>
    <executions>
        <execution>
            <id>install node and npm</id>
            <goals>
                <goal>install-node-and-npm</goal>
            </goals>
        </execution>
        <execution>
            <id>npm install</id>
            <goals>
                <goal>npm</goal>
            </goals>
        </execution>
        <execution>
            <id>npm run build</id>
            <goals>
                <goal>npm</goal>
            </goals>
            <configuration>
                <arguments>run build</arguments>
            </configuration>
        </execution>
    </executions>
</plugin>
```
