# `/layout-parsing` 接口文档

> 适用范围：PaddleOCR-VL / PP-StructureV3 等文档解析产线的服务化部署（PaddleX Serving，默认端口 `8080`）。
>
> 依据来源：
> - 官方产线文档：`docs/version3.x/pipeline_usage/PaddleOCR-VL.md`（§服务化部署）
> - 响应解析代码：`mcp_server/paddleocr_mcp/inference/shared/http_result_parsers.py`、`api_sdk/typescript/src/client.ts`（`parseDocParsingResult`）、`api_sdk/go/ocr.go`

---

## 1. 接口概览

| 项 | 说明 |
|----|------|
| 路径 | `POST /layout-parsing` |
| Content-Type | `application/json` |
| 功能 | 版面解析（文档 → Markdown / 结构化结果） |
| 变体接口 | `POST /layout-parsing/form-data`（multipart 上传，本仓库 `patches/` 自定义补丁新增，字段名与 JSON 入参一致） |

---

## 2. 请求参数（入参）

| 参数 | 类型 | 含义 | 是否必填 |
|------|------|------|----------|
| `file` | `string` | 服务器可访问的图像文件（含 TIFF，多页时按页处理）或 PDF 文件的 URL，或上述类型文件内容的 Base64 编码结果 | **是** |
| `fileType` | `integer` \| `null` | 文件类型。`0` = PDF，`1` = 图像（含 TIFF）。若缺省则根据 URL 推断 | 否 |
| `useDocOrientationClassify` | `boolean` \| `null` | 是否启用文档方向分类（对应 `predict` 的 `use_doc_orientation_classify`） | 否 |
| `useDocUnwarping` | `boolean` \| `null` | 是否启用文档扭曲矫正（对应 `use_doc_unwarping`） | 否 |
| `useLayoutDetection` | `boolean` \| `null` | 是否启用版面检测（对应 `use_layout_detection`） | 否 |
| `useChartRecognition` | `boolean` \| `null` | 是否启用图表识别（对应 `use_chart_recognition`） | 否 |
| `useSealRecognition` | `boolean` \| `null` | 是否启用印章识别（对应 `use_seal_recognition`） | 否 |
| `useOcrForImageBlock` | `boolean` \| `null` | 是否对图像块执行 OCR（对应 `use_ocr_for_image_block`） | 否 |
| `layoutThreshold` | `number` \| `object` \| `null` | 版面检测置信度阈值（对应 `layout_threshold`） | 否 |
| `layoutNms` | `boolean` \| `null` | 版面检测是否启用 NMS（对应 `layout_nms`） | 否 |
| `layoutUnclipRatio` | `number` \| `array` \| `object` \| `null` | 检测框外扩比例（对应 `layout_unclip_ratio`） | 否 |
| `layoutMergeBboxesMode` | `string` \| `object` \| `null` | 检测框合并模式（对应 `layout_merge_bboxes_mode`） | 否 |
| `layoutShapeMode` | `string` | 检测框形状模式（对应 `layout_shape_mode`） | 否 |
| `promptLabel` | `string` \| `null` | VLM 提示标签（对应 `prompt_label`） | 否 |
| `formatBlockContent` | `boolean` \| `null` | 是否在 JSON 中保存格式化后的块内容（对应 `format_block_content`） | 否 |
| `repetitionPenalty` | `number` \| `null` | VLM 生成重复惩罚（对应 `repetition_penalty`） | 否 |
| `temperature` | `number` \| `null` | VLM 采样温度（对应 `temperature`） | 否 |
| `topP` | `number` \| `null` | VLM 采样 top-p（对应 `top_p`） | 否 |
| `minPixels` | `number` \| `null` | 输入图像最小像素限制（对应 `min_pixels`） | 否 |
| `maxPixels` | `number` \| `null` | 输入图像最大像素限制（对应 `max_pixels`） | 否 |
| `maxNewTokens` | `number` \| `null` | VLM 最大生成 token 数（对应 `max_new_tokens`） | 否 |
| `mergeLayoutBlocks` | `boolean` \| `null` | 是否合并版面块（对应 `merge_layout_blocks`） | 否 |
| `markdownIgnoreLabels` | `array` \| `null` | Markdown 输出中忽略的版面标签列表（对应 `markdown_ignore_labels`） | 否 |
| `vlmExtraArgs` | `object` \| `null` | VLM 额外参数（对应 `vlm_extra_args`） | 否 |
| `prettifyMarkdown` | `boolean` | 是否输出美化后的 Markdown 文本。默认 `true` | 否 |
| `showFormulaNumber` | `boolean` | Markdown 中是否包含公式编号。默认 `false` | 否 |
| `restructurePages` | `boolean` | 是否重构多页结果。默认 `false` | 否 |
| `mergeTables` | `boolean` | 合并跨页表格（对应 `restructure_pages` 的 `merge_tables`），仅当 `restructurePages=true` 时生效 | 否 |
| `relevelTitles` | `boolean` | 重建多级标题（对应 `relevel_titles`），仅当 `restructurePages=true` 时生效 | 否 |
| `returnMarkdownImages` | `boolean` | 是否返回 Markdown 中引用的图片。默认 `true`；为 `false` 时 `markdown.images` 为 `null` 或不出现 | 否 |
| `outputFormats` | `array` \| `null` | 需要额外返回的文档格式列表，当前仅支持 `"docx"`。默认不返回 | 否 |
| `visualize` | `boolean` \| `null` | 是否返回可视化结果图与中间图像。未传或 `null` 时遵循配置 `Serving.visualize`；两者均未设置时默认返回 | 否 |

---

## 3. 响应参数（返回）

### 3.1 外层结构

| 字段 | 类型 | 含义 |
|------|------|------|
| `logId` | `string` | 日志追踪 ID |
| `errorCode` | `integer` | 错误码，`0` 表示成功 |
| `errorMsg` | `string` | 错误说明，成功时为 `"Success"` |
| `result` | `object` | 业务结果，见下表 |

### 3.2 `result` 对象

| 字段 | 类型 | 含义 |
|------|------|------|
| `layoutParsingResults` | `array` | 版面解析结果。长度为 1（图像输入）或实际处理的页数（PDF 输入），每个元素依次对应处理的一页 |
| `dataInfo` | `object` | 输入数据信息 |

### 3.3 `layoutParsingResults[]` 数组元素

| 字段 | 类型 | 含义 |
|------|------|------|
| `prunedResult` | `object` | `predict` 结果 JSON（`res` 字段）的简化版本，去除 `input_path` 与 `page_index`。内部结构见 3.4 |
| `markdown` | `object` | Markdown 结果，见 3.5 |
| `outputImages` | `object` \| `null` | 可视化结果图键值对（版面检测可视化、阅读顺序可视化等）。JPEG 格式，默认 Base64；开启 URL 返回模式时为预签名 URL |
| `inputImage` | `string` \| `null` | 输入图像。JPEG 格式，默认 Base64；开启 URL 返回模式时为预签名 URL |
| `exports` | `object` \| `null` | 附加导出结果，仅当请求含 `outputFormats` 且列出对应格式时出现，如 `{"docx": {"content": "<Base64>"}}` |

### 3.4 `prunedResult` 对象（结构化解析结果）

| 字段 | 类型 | 含义 |
|------|------|------|
| `model_settings` | `object` | 产线实际生效的模型配置 |
| `model_settings.use_doc_preprocessor` | `boolean` | 是否启用了文档预处理子产线 |
| `model_settings.use_layout_detection` | `boolean` | 是否启用了版面分析模块 |
| `model_settings.use_chart_recognition` | `boolean` | 是否启用了图表识别 |
| `model_settings.format_block_content` | `boolean` | 是否保存格式化后的块内容 |
| `doc_preprocessor_res` | `object` | 文档预处理子产线输出，**仅当启用时存在** |
| `doc_preprocessor_res.model_settings` | `object` | 含 `use_doc_orientation_classify`、`use_doc_unwarping` |
| `doc_preprocessor_res.angle` | `integer` | 方向分类预测角度（启用时返回实际值） |
| `parsing_res_list` | `array` | 解析结果列表，**顺序即阅读顺序**，每个元素见下 |

`parsing_res_list[]` 数组元素：

| 字段 | 类型 | 含义 |
|------|------|------|
| `block_bbox` | `array`（4 元素） | 版面区域边界框 `[x1, y1, x2, y2]` |
| `block_label` | `string` | 版面区域标签，如 `text`、`table`、`figure`、`formula`、`seal` 等 |
| `block_content` | `string` | 该版面区域内的识别内容（文本/表格 HTML/公式等） |
| `block_id` | `integer` | 版面区域索引，表示排序结果 |
| `block_order` | `integer` \| `null` | 阅读顺序编号，非排序部分为 `null` |

### 3.5 `markdown` 对象

| 字段 | 类型 | 含义 |
|------|------|------|
| `text` | `string` | Markdown 文本（图片以相对路径引用 `images` 中的键） |
| `images` | `object` \| `null` | Markdown 图片相对路径 → 图像内容的键值对。默认 Base64；开启 URL 返回模式时为预签名 URL。`returnMarkdownImages=false` 时为 `null` 或不出现 |

> 说明：`outputImages`、`inputImage`、`markdown.images`、`exports` 等二进制字段默认 Base64 内联；服务端开启 URL 返回模式后变为预签名 URL，字段类型不变。

---

## 4. 请求示例

### 4.1 Python

```python
import base64
import requests

BASE_URL = "http://localhost:8080"

with open("./demo.jpg", "rb") as file:
    image_data = base64.b64encode(file.read()).decode("ascii")

payload = {
    "file": image_data,          # 图片 URL 或 Base64
    "fileType": 1,               # 0=PDF, 1=图像
    "visualize": False,          # 不返回可视化图，节省带宽
    "outputFormats": ["docx"],   # 额外导出 Word
}
response = requests.post(BASE_URL + "/layout-parsing", json=payload)
result = response.json()["result"]

for page in result["layoutParsingResults"]:
    print(page["markdown"]["text"])          # Markdown 文本
    print(page["prunedResult"]["parsing_res_list"])  # 结构化块结果
```

### 4.2 curl

```bash
IMG_B64=$(base64 -w0 demo.jpg)
curl -X POST http://localhost:8080/layout-parsing \
    -H "Content-Type: application/json" \
    -d "{\"file\": \"${IMG_B64}\", \"fileType\": 1}"
```

### 4.3 multipart/form-data 变体（本仓库补丁接口）

```bash
curl -X POST http://localhost:8080/layout-parsing/form-data \
    -F "file=@./test.pdf" \
    -F "fileType=0" \
    -F "visualize=false"
```

`fileType` 缺省时按文件后缀推断（`.pdf` → `0`，图像后缀 → `1`）；其余参数可作独立字段（值按 JSON 解析），也可通过 `params` 字段整体传入。

---

## 5. 响应示例

```json
{
  "logId": "...",
  "errorCode": 0,
  "errorMsg": "Success",
  "result": {
    "layoutParsingResults": [
      {
        "prunedResult": {
          "model_settings": {
            "use_doc_preprocessor": true,
            "use_layout_detection": true,
            "use_chart_recognition": false,
            "format_block_content": true
          },
          "doc_preprocessor_res": {
            "model_settings": {
              "use_doc_orientation_classify": true,
              "use_doc_unwarping": false
            },
            "angle": 0
          },
          "parsing_res_list": [
            {
              "block_bbox": [72, 68, 540, 130],
              "block_label": "title",
              "block_content": "# 文档标题",
              "block_id": 0,
              "block_order": 0
            },
            {
              "block_bbox": [72, 150, 540, 420],
              "block_label": "text",
              "block_content": "正文内容……",
              "block_id": 1,
              "block_order": 1
            }
          ]
        },
        "markdown": {
          "text": "# 文档标题\n\n正文内容……",
          "images": {}
        },
        "outputImages": { },
        "inputImage": "<Base64>",
        "exports": { "docx": { "content": "<Base64>" } }
      }
    ],
    "dataInfo": { }
  }
}
```

失败时：

```json
{
  "logId": "...",
  "errorCode": <HTTP 状态码>,
  "errorMsg": "<错误描述>"
}
```

---

## 6. 相关代码索引

| 文件 | 作用 |
|------|------|
| `api_sdk/typescript/src/client.ts`（`parseDocParsingResult`） | TS SDK 对响应结构的解析实现（`markdown.text/images`、`outputImages`、`prunedResult`、`inputImage`、`exports`） |
| `api_sdk/go/ocr.go` | Go SDK 响应结构体（含 `prunedResult` 等字段映射） |
| `mcp_server/paddleocr_mcp/inference/shared/http_result_parsers.py`（`parse_doc_parsing_result`） | MCP Server 对 `layoutParsingResults` 的解析 |
| `mcp_server/paddleocr_mcp/inference/paddleocr_vl/self_hosted.py` | 自部署模式下调用 `layout-parsing` 端点的客户端 |
| `patches/layout_parsing_form_data.py` | `/layout-parsing/form-data` 变体接口实现（form-data → JSON → ASGI 进程内转发） |
| `docs/version3.x/pipeline_usage/PaddleOCR-VL.md` | 官方产线文档（入参/返回参数权威来源） |
