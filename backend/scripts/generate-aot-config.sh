#!/bin/bash
# ===========================================
# Spring Native AOT 配置生成脚本
# 用于为各微服务生成 GraalVM Native Image 反射配置
# ===========================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(dirname "$SCRIPT_DIR")"
RESOURCES_DIR="$BACKEND_DIR/common/src/main/resources"

echo "============================================"
echo "Spring Native AOT 配置生成工具"
echo "============================================"

# 通用反射配置模板
generate_common_reflection_config() {
    cat > "$RESOURCES_DIR/META-INF/native-image/reflection-config.json" << 'EOF'
[
  {
    "name": "java.lang.String",
    "allDeclaredConstructors": true,
    "allDeclaredMethods": true,
    "allDeclaredFields": true
  },
  {
    "name": "java.lang.Integer",
    "allDeclaredConstructors": true,
    "allDeclaredMethods": true
  },
  {
    "name": "java.lang.Long",
    "allDeclaredConstructors": true,
    "allDeclaredMethods": true
  },
  {
    "name": "java.lang.Boolean",
    "allDeclaredConstructors": true,
    "allDeclaredMethods": true
  },
  {
    "name": "java.lang.Double",
    "allDeclaredConstructors": true,
    "allDeclaredMethods": true
  },
  {
    "name": "java.math.BigDecimal",
    "allDeclaredConstructors": true,
    "allDeclaredMethods": true
  },
  {
    "name": "java.util.HashMap",
    "allDeclaredConstructors": true,
    "allDeclaredMethods": true
  },
  {
    "name": "java.util.ArrayList",
    "allDeclaredConstructors": true,
    "allDeclaredMethods": true
  },
  {
    "name": "java.util.LinkedHashMap",
    "allDeclaredConstructors": true,
    "allDeclaredMethods": true
  },
  {
    "name": "java.time.LocalDateTime",
    "allDeclaredConstructors": true,
    "allDeclaredMethods": true
  },
  {
    "name": "java.time.LocalDate",
    "allDeclaredConstructors": true,
    "allDeclaredMethods": true
  }
]
EOF
    echo "✅ 生成通用反射配置: $RESOURCES_DIR/META-INF/native-image/reflection-config.json"
}

# 生成 Spring Native 自动配置
generate_native_properties() {
    cat > "$RESOURCES_DIR/META-INF/native-image/native-image.properties" << 'EOF'
# Spring Native Image 配置
# https://docs.spring.io/spring-native/docs/current/reference/htmlsingle/

# 启用 Spring AOT
org.springframework.aot=true

# 启用 Native Hints 自动处理
org.springframework.native.image.hints=true

# JVM 参数
java.vm.argLine=-Xmx256m

# 启用安全检查
org.springframework.native.image.security.allowInsecure=true
EOF
    echo "✅ 生成 Native Image 属性: $RESOURCES_DIR/META-INF/native-image/native-image.properties"
}

# 生成资源访问配置
generate_resource_config() {
    cat > "$RESOURCES_DIR/META-INF/native-image/resource-config.json" << 'EOF'
{
  "resources": {
    "includes": [
      {
        "pattern": "org/springframework/cboot/**/*.properties"
      },
      {
        "pattern": "META-INF/services/*"
      },
      {
        "pattern": "META-INF/spring/**"
      },
      {
        "pattern": "application*.yml"
      },
      {
        "pattern": "application*.yaml"
      },
      {
        "pattern": "application*.properties"
      }
    ],
    "excludes": [
      {
        "pattern": "**.class"
      }
    ]
  }
}
EOF
    echo "✅ 生成资源访问配置: $RESOURCES_DIR/META-INF/native-image/resource-config.json"
}

# 生成序列化配置（用于 JSON 处理）
generate_serialization_config() {
    cat > "$RESOURCES_DIR/META-INF/native-image/serialization-config.json" << 'EOF'
[
  {
    "name": "java.util.HashMap",
    "allDeclaredConstructors": true,
    "allDeclaredMethods": true
  },
  {
    "name": "java.util.ArrayList",
    "allDeclaredConstructors": true,
    "allDeclaredMethods": true
  },
  {
    "name": "java.util.LinkedHashMap",
    "allDeclaredConstructors": true,
    "allDeclaredMethods": true
  },
  {
    "name": "java.time.LocalDateTime",
    "allDeclaredConstructors": true,
    "allDeclaredMethods": true
  },
  {
    "name": "java.time.LocalDate",
    "allDeclaredConstructors": true,
    "allDeclaredMethods": true
  }
]
EOF
    echo "✅ 生成序列化配置: $RESOURCES_DIR/META-INF/native-image/serialization-config.json"
}

# 生成 JVM 参数配置
generate_jvm_config() {
    cat > "$RESOURCES_DIR/META-INF/native-image/jvm-config.json" << 'EOF'
[
  "-XX:+UseG1GC",
  "-XX:+UseStringDeduplication",
  "-XX:+OptimizeStringConcat"
]
EOF
    echo "✅ 生成 JVM 配置: $RESOURCES_DIR/META-INF/native-image/jvm-config.json"
}

# 创建 README 说明文档
generate_readme() {
    cat > "$RESOURCES_DIR/META-INF/native-image/README.md" << 'EOF'
# Spring Native AOT 配置说明

本目录包含 GraalVM Native Image 构建所需的 AOT（Ahead-of-Time）配置文件。

## 文件说明

| 文件 | 用途 |
|------|------|
| `reflection-config.json` | 反射 API 注册配置 |
| `resource-config.json` | 资源文件访问配置 |
| `serialization-config.json` | 序列化配置 |
| `native-image.properties` | Native Image 构建属性 |
| `jvm-config.json` | JVM 参数配置 |

## Spring Boot 3.x AOT 自动处理

Spring Boot 3.x 内置 AOT 引擎，会自动处理：

1. **@ConfigurationProperties** - 配置属性绑定
2. **@Entity 扫描** - JPA/MongoDB 实体
3. **Spring Factories** - 自动服务发现
4. **反射用途** - 大部分 Spring 框架反射

## 手动配置场景

需要手动添加反射配置的典型场景：

1. **自定义 Jackson 序列化** - 自定义序列化器
2. **动态类加载** - Class.forName() 调用
3. **JMX 暴露** - Management 接口
4. **第三方库** - 非 Spring 管理的组件

## 添加自定义反射配置

在 `reflection-config.json` 中添加：

```json
{
  "name": "com.example.MyClass",
  "allDeclaredConstructors": true,
  "allDeclaredMethods": true,
  "allDeclaredFields": true
}
```

## 验证构建

```bash
# 本地构建 Native Image
./gradlew :product-service:bootBuildImage

# 或本地运行测试
./gradlew :product-service:nativeRun
```

## 参考资料

- [Spring Native Reference](https://docs.spring.io/spring-native/docs/current/reference/html/)
- [GraalVM Native Image](https://www.graalvm.org/native-image/)
- [Native Image 反射配置](https://www.graalvm.org/latest/reference-manual/native-image/dynamic-features/Reflection/)
EOF
    echo "✅ 生成配置说明文档"
}

# 主流程
main() {
    echo ""
    echo "开始生成 AOT 配置文件..."
    echo ""

    generate_common_reflection_config
    generate_native_properties
    generate_resource_config
    generate_serialization_config
    generate_jvm_config
    generate_readme

    echo ""
    echo "============================================"
    echo "✅ AOT 配置生成完成！"
    echo "============================================"
    echo ""
    echo "下一步："
    echo "  1. 运行 ./gradlew build 构建项目"
    echo "  2. 使用 ./gradlew :product-service:bootBuildImage 构建 Native 镜像"
    echo "  3. 测试 docker-compose -f docker-compose.yml -f docker-compose.native.yml up -d"
    echo ""
}

main "$@"
