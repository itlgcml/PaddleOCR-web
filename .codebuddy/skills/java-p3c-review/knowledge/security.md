# 四、安全规约

## SEC-01 【强制】隶属于用户个人的页面或者功能必须进行权限控制校验。

**说明**：防止没有做水平权限校验就可随意访问、修改、删除别人的数据，比如查看他人的私信内容、修改他人的订单。

### 正例

```java
@GetMapping("/user/message/{userId}")
public Result getUserMessage(@PathVariable Long userId, @LoginUser User currentUser) {
    // 权限校验：只能查看自己的消息
    if (!currentUser.getId().equals(userId)) {
        throw new AccessDeniedException("无权查看其他用户的消息");
    }
    return Result.success(messageService.getByUserId(userId));
}
```

### 反例

```java
@GetMapping("/user/message/{userId}")
public Result getUserMessage(@PathVariable Long userId) {
    // ❌ 未进行权限校验，可查看任意用户的消息
    return Result.success(messageService.getByUserId(userId));
}
```

---

## SEC-02 【强制】用户敏感数据禁止直接展示，必须对展示数据进行脱敏。

**说明**：个人手机号码显示为：`158****9119`，隐藏中间 4 位，防止隐私泄露。身份证号显示为 `110101********1234`，银行卡号显示为 `6222****1234`。

### 正例

```java
public class UserDTO {
    private Long id;
    private String username;
    private String mobile;      // 原始值：15812349119
    private String idCard;      // 原始值：110101199001011234

    // 返回脱敏后的手机号
    public String getDesensitizedMobile() {
        if (mobile == null || mobile.length() != 11) {
            return mobile;
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(7);
    }

    // 返回脱敏后的身份证号
    public String getDesensitizedIdCard() {
        if (idCard == null || idCard.length() != 18) {
            return idCard;
        }
        return idCard.substring(0, 6) + "********" + idCard.substring(14);
    }
}
```

### 工具类实现

```java
public class DesensitizationUtil {
    /**
     * 手机号脱敏
     */
    public static String mobile(String mobile) {
        if (mobile == null || mobile.length() != 11) {
            return mobile;
        }
        return mobile.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2");
    }

    /**
     * 身份证号脱敏
     */
    public static String idCard(String idCard) {
        if (idCard == null || idCard.length() != 18) {
            return idCard;
        }
        return idCard.replaceAll("(\\d{6})\\d{8}(\\d{4})", "$1********$2");
    }

    /**
     * 银行卡号脱敏
     */
    public static String bankCard(String bankCard) {
        if (bankCard == null || bankCard.length() < 8) {
            return bankCard;
        }
        return bankCard.replaceAll("(\\d{4})\\d+(\\d{4})", "$1****$2");
    }

    /**
     * 姓名脱敏（保留姓氏）
     */
    public static String name(String name) {
        if (name == null || name.length() <= 1) {
            return name;
        }
        return name.charAt(0) + "*".repeat(name.length() - 1);
    }
}
```

---

## SEC-03 【强制】用户输入的 SQL 参数严格使用参数绑定或者 METADATA 字段值限定，防止 SQL 注入，禁止字符串拼接 SQL 访问数据库。

**说明**：SQL 注入是最常见的安全漏洞之一，攻击者可以通过构造特殊的输入来执行任意 SQL 语句，导致数据泄露、篡改或删除。

### 正例 - MyBatis 参数绑定

```java
// Mapper 接口
@Select("SELECT * FROM user WHERE id = #{userId}")
User getById(@Param("userId") Long userId);

// 使用 #{} 进行参数绑定，会自动转义特殊字符
@Select("SELECT * FROM user WHERE name = #{name} AND status = #{status}")
List<User> findByNameAndStatus(@Param("name") String name, @Param("status") Integer status);
```

### 反例 - 字符串拼接 SQL（危险！）

```java
// ❌ 严重错误：使用 ${} 或直接拼接 SQL
@Select("SELECT * FROM user WHERE name = '${name}'")
List<User> findByNameUnsafe(@Param("name") String name);

// 如果 name 传入: ' OR '1'='1
// 最终 SQL: SELECT * FROM user WHERE name = '' OR '1'='1'
// 这将返回所有用户数据！

// ❌ 严重错误：Java 代码中拼接 SQL
public List<User> search(String keyword) {
    String sql = "SELECT * FROM user WHERE name LIKE '%" + keyword + "%'";
    return jdbcTemplate.query(sql, new UserRowMapper());
}
```

### 正例 - 使用 PreparedStatement

```java
public List<User> searchSafe(String keyword) {
    String sql = "SELECT * FROM user WHERE name LIKE ?";
    // 使用 ? 占位符，JDBC 会自动处理转义
    return jdbcTemplate.query(sql, ps -> {
        ps.setString(1, "%" + keyword + "%");
    }, new UserRowMapper());
}
```

### 正例 - 使用 JPA Criteria API

```java
public List<User> searchWithCriteria(String keyword) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<User> query = cb.createQuery(User.class);
    Root<User> root = query.from(User.class);

    // 安全的方式构建查询
    query.where(cb.like(root.get("name"), "%" + keyword + "%"));

    return entityManager.createQuery(query).getResultList();
}
```

---

## SEC-04 【强制】用户请求传入的任何参数必须做有效性验证。

**说明**：忽略参数校验可能导致：
- page size 过大导致内存溢出
- 恶意 order by 导致数据库慢查询
- 任意重定向
- SQL 注入
- 反序列化注入
- 正则输入源串拒绝服务 ReDoS

**说明**：Java 代码用正则来验证客户端的输入，有些正则写法验证普通用户输入没有问题，但是如果攻击人员使用的是特殊构造的字符串来验证，有可能导致死循环的结果。

### 正例 - 参数校验

```java
@PostMapping("/users")
public Result createUser(@RequestBody @Valid UserCreateRequest request) {
    // 使用 @Valid 自动校验
    return Result.success(userService.create(request));
}

// 请求 DTO
public class UserCreateRequest {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 20, message = "用户名长度为2-20个字符")
    private String username;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String mobile;

    @Email(message = "邮箱格式不正确")
    private String email;

    @Min(value = 1, message = "每页最少1条")
    @Max(value = 100, message = "每页最多100条")
    private Integer pageSize = 10;
}
```

### 正例 - 分页参数限制

```java
@Service
public class UserService {
    private static final int MAX_PAGE_SIZE = 100;

    public Page<User> list(int pageNum, int pageSize) {
        // 限制分页大小，防止内存溢出
        if (pageSize > MAX_PAGE_SIZE) {
            pageSize = MAX_PAGE_SIZE;
        }
        if (pageSize < 1) {
            pageSize = 10;
        }
        return userMapper.selectPage(new Page<>(pageNum, pageSize));
    }
}
```

### 正例 - 排序字段白名单

```java
@Service
public class OrderService {
    // 允许的排序字段白名单
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
        "id", "create_time", "update_time", "amount", "status"
    );

    public List<Order> list(String sortField, String sortOrder) {
        // 校验排序字段
        if (!ALLOWED_SORT_FIELDS.contains(sortField)) {
            throw new IllegalArgumentException("不支持的排序字段: " + sortField);
        }

        // 限制排序方向
        if (!"ASC".equalsIgnoreCase(sortOrder) && !"DESC".equalsIgnoreCase(sortOrder)) {
            sortOrder = "DESC";
        }

        return orderMapper.selectList(sortField, sortOrder);
    }
}
```

### 反例 - 缺少参数校验

```java
@GetMapping("/api/data")
public List<Data> getData(int pageSize, String orderBy) {
    // ❌ 未校验 pageSize，可能传入 100000 导致内存溢出
    // ❌ 未校验 orderBy，可能传入恶意 SQL
    return dataService.query(pageSize, orderBy);
}
```

### ReDoS 防护

```java
// ❌ 危险：复杂的正则可能导致 ReDoS
public boolean validateEmail(String email) {
    // 这个正则可能会被精心构造的字符串导致灾难性回溯
    return email.matches("^([a-zA-Z0-9_\\-\\.]+)@([a-zA-Z0-9_\\-\\.]+)\\.([a-zA-Z]{2,5})$");
}

// ✅ 安全：限制输入长度，使用简单的正则
public boolean validateEmailSafe(String email) {
    if (email == null || email.length() > 100) {
        return false;
    }
    // 更简单的正则，或使用专门的库
    return email.contains("@") && EMAIL_PATTERN.matcher(email).matches();
}
```

---

## SEC-05 【强制】禁止向 HTML 页面输出未经安全过滤或未正确转义的用户数据。

**说明**：此条规则防止 XSS（跨站脚本攻击），攻击者可以注入恶意脚本窃取用户 cookie、会话令牌或其他敏感信息。

### 正例 - 使用 Thymeleaf 自动转义

```java
@Controller
public class UserController {
    @GetMapping("/user/profile")
    public String profile(@RequestParam String username, Model model) {
        // Thymeleaf 默认会对输出进行 HTML 转义
        model.addAttribute("username", username);
        return "user/profile";
    }
}
```

```html
<!-- Thymeleaf 模板 - 自动转义 -->
<div th:text="${username}">User Name</div>

<!-- 如果需要输出原始 HTML（不推荐），使用 th:utext，但要确保数据可信 -->
<div th:utext="${trustedHtmlContent}">Trusted Content</div>
```

### 正例 - 手动 XSS 过滤

```java
public class XssFilterUtil {
    public static String stripXss(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        value = value.replaceAll("<", "&lt;")
                     .replaceAll(">", "&gt;")
                     .replaceAll("\"", "&quot;")
                     .replaceAll("'", "&#x27;")
                     .replaceAll("&", "&amp;");

        // 移除危险标签
        value = value.replaceAll("(?i)<script.*?>.*?</script.*?>", "")
                     .replaceAll("(?i)<.*?javascript:.*?>", "")
                     .replaceAll("(?i)<.*?on\\w+\\s*=.*?>", "");

        return value;
    }
}
```

### 反例 - 直接输出用户输入

```java
@Controller
public class SearchController {
    @GetMapping("/search")
    @ResponseBody
    public String search(@RequestParam String keyword) {
        // ❌ 直接将用户输入拼接到 HTML 中
        return "<div>搜索结果：" + keyword + "</div>";
        // 如果 keyword = <script>alert('xss')</script>
        // 将会执行恶意脚本！
    }
}
```

---

## SEC-06 【强制】表单、AJAX 提交必须执行 CSRF 安全过滤。

**说明**：CSRF（Cross-site request forgery）跨站请求伪造是一类常见编程漏洞。对于存在 CSRF 漏洞的应用/网站，攻击者可以事先构造好 URL，只要受害者用户一访问，后台便在用户不知情情况下对数据库中用户参数进行相应修改。

### 正例 - Spring Security CSRF 保护

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            )
            .authorizeHttpRequests(auth -> auth
                .anyRequest().authenticated()
            );
        return http.build();
    }
}
```

### 正例 - 前端携带 CSRF Token

```html
<!-- 在表单中包含 CSRF Token -->
<form action="/transfer" method="post">
    <input type="hidden" name="_csrf" value="${_csrf.token}"/>
    <input type="text" name="amount"/>
    <button type="submit">转账</button>
</form>

<!-- AJAX 请求携带 CSRF Token -->
<script>
fetch('/api/transfer', {
    method: 'POST',
    headers: {
        'X-CSRF-TOKEN': document.querySelector('meta[name="_csrf"]').content
    },
    body: JSON.stringify({amount: 100})
});
</script>
```

### 正例 - 双重 Cookie 验证

```java
@RestController
public class TransferController {
    @PostMapping("/api/transfer")
    public Result transfer(@RequestBody TransferRequest request,
                           @CookieValue("csrf_token") String csrfToken,
                           @RequestHeader("X-CSRF-Token") String headerToken) {
        // 验证 CSRF Token
        if (!csrfToken.equals(headerToken)) {
            throw new SecurityException("CSRF Token 验证失败");
        }
        // 执行转账...
    }
}
```

---

## SEC-07 【强制】在使用平台资源，譬如短信、邮件、电话、下单、支付，必须实现正确的防重放限制，如数量限制、疲劳度控制、验证码校验，避免被滥刷导致资损。

**说明**：如注册时发送验证码到手机，如果没有限制次数和频率，那么可以利用此功能骚扰到其它用户，并造成短信平台资源浪费。

### 正例 - 短信验证码防刷

```java
@Service
public class SmsService {
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final int MAX_SEND_PER_DAY = 5;
    private static final int SEND_INTERVAL_SECONDS = 60;

    public void sendVerifyCode(String mobile) {
        String dayKey = "sms:day:" + mobile + ":" + LocalDate.now();
        String intervalKey = "sms:interval:" + mobile;

        // 检查当天发送次数
        String dayCount = redisTemplate.opsForValue().get(dayKey);
        if (dayCount != null && Integer.parseInt(dayCount) >= MAX_SEND_PER_DAY) {
            throw new BusinessException("今天发送次数已达上限，请明天再试");
        }

        // 检查发送间隔
        if (Boolean.TRUE.equals(redisTemplate.hasKey(intervalKey))) {
            throw new BusinessException("发送过于频繁，请" + SEND_INTERVAL_SECONDS + "秒后再试");
        }

        // 生成验证码并发送
        String code = generateCode();
        sendSms(mobile, code);

        // 记录发送次数
        redisTemplate.opsForValue().increment(dayKey);
        redisTemplate.expire(dayKey, 1, TimeUnit.DAYS);

        // 设置发送间隔
        redisTemplate.opsForValue().set(intervalKey, "1", SEND_INTERVAL_SECONDS, TimeUnit.SECONDS);

        // 缓存验证码（5分钟有效）
        String codeKey = "sms:code:" + mobile;
        redisTemplate.opsForValue().set(codeKey, code, 5, TimeUnit.MINUTES);
    }

    public boolean verifyCode(String mobile, String inputCode) {
        String codeKey = "sms:code:" + mobile;
        String storedCode = redisTemplate.opsForValue().get(codeKey);

        if (storedCode == null) {
            return false;
        }

        boolean matched = storedCode.equals(inputCode);
        if (matched) {
            // 验证成功后删除验证码（一次性使用）
            redisTemplate.delete(codeKey);
        }
        return matched;
    }
}
```

### 正例 - 接口限流

```java
@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        String clientIP = getClientIP(request);
        String key = "ratelimit:" + request.getRequestURI() + ":" + clientIP;

        Long count = redisTemplate.opsForValue().increment(key);
        if (count == 1) {
            // 第一次访问，设置过期时间
            redisTemplate.expire(key, 1, TimeUnit.MINUTES);
        }

        if (count > 100) {  // 每分钟最多100次
            response.setStatus(429);  // Too Many Requests
            response.getWriter().write("请求过于频繁，请稍后重试");
            return false;
        }
        return true;
    }
}
```

---

## SEC-08 【推荐】发贴、评论、发送即时消息等用户生成内容的场景必须实现防刷、文本内容违禁词过滤等风控策略。

### 正例 - 内容安全过滤

```java
@Service
public class ContentSecurityService {
    private static final Set<String> SENSITIVE_WORDS = Set.of(
        "违法词1", "违法词2", "广告词1"
    );

    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
        String.join("|", SENSITIVE_WORDS),
        Pattern.CASE_INSENSITIVE
    );

    /**
     * 检查内容是否包含违禁词
     */
    public ContentCheckResult checkContent(String content) {
        if (content == null || content.isEmpty()) {
            return ContentCheckResult.pass();
        }

        Matcher matcher = SENSITIVE_PATTERN.matcher(content);
        if (matcher.find()) {
            return ContentCheckResult.fail("内容包含敏感词：" + matcher.group());
        }

        return ContentCheckResult.pass();
    }

    /**
     * 替换敏感词
     */
    public String filterContent(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        return SENSITIVE_PATTERN.matcher(content)
            .replaceAll(match -> "*".repeat(match.group().length()));
    }
}
```

### 正例 - 发帖频率控制

```java
@Service
public class PostService {
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final int MAX_POSTS_PER_HOUR = 10;

    public void createPost(Long userId, PostCreateRequest request) {
        // 检查违禁词
        ContentCheckResult check = contentSecurityService.checkContent(request.getContent());
        if (!check.isPassed()) {
            throw new BusinessException(check.getMessage());
        }

        // 检查发帖频率
        String key = "post:rate:" + userId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == 1) {
            redisTemplate.expire(key, 1, TimeUnit.HOURS);
        }

        if (count > MAX_POSTS_PER_HOUR) {
            throw new BusinessException("发帖过于频繁，请稍后再试");
        }

        // 创建帖子...
    }
}
```

---

## 安全检查清单

| 检查项 | 等级 | 检查方法 |
|--------|------|----------|
| 权限控制校验 | 【强制】 | 代码审查、自动化测试 |
| 敏感数据脱敏 | 【强制】 | 代码审查、UI 测试 |
| SQL 参数绑定 | 【强制】 | 静态分析（SpotBugs、SonarQube） |
| 输入参数校验 | 【强制】 | 代码审查、Fuzz 测试 |
| XSS 防护 | 【强制】 | 代码审查、自动化扫描 |
| CSRF 防护 | 【强制】 | 配置审查、渗透测试 |
| 防重放限制 | 【强制】 | 代码审查、接口测试 |
| 内容风控 | 【推荐】 | 代码审查、人工审核 |

---

## 安全工具推荐

| 工具 | 用途 | 集成方式 |
|------|------|----------|
| OWASP Dependency-Check | 依赖漏洞扫描 | Maven/Gradle 插件 |
| SpotBugs | 静态代码分析 | IDE 插件、CI 集成 |
| SonarQube | 代码质量和安全 | 代码审查流水线 |
| SQLMap | SQL 注入测试 | 安全测试阶段 |
| Burp Suite | Web 安全测试 | 渗透测试 |

---

*安全规约 v1.1 | P3C Skill*
