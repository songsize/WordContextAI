# WordContext AI

WordContext AI是一款智能词汇学习工具，通过生成式AI技术为您创建包含目标词汇的定制化文章，帮助您在真实语境中掌握词汇用法。

## 功能特点

- **智能文章生成**：基于DeepSeek AI的上下文生成引擎
- **词汇高亮显示**：自动高亮目标词汇，便于学习
- **多种文章风格**：支持学术、日常、商务、文学四种写作风格
- **中英文双语支持**：可生成中文或英文文章
- **一键复制分享**：方便保存和分享生成的文章
- **Material Design 3**：现代化的用户界面设计

## 技术架构

- **架构模式**：MVVM (Model-View-ViewModel)
- **网络请求**：Retrofit + OkHttp
- **异步处理**：Kotlin Coroutines
- **UI框架**：Material Design 3 + ViewBinding
- **数据管理**：LiveData + ViewModel

## 使用方法

1. **输入单词**：在底部输入框中输入您想要学习的单词
2. **选择设置**：点击右上角设置按钮，选择文章风格和语言
3. **生成文章**：点击发送按钮，AI将为您生成包含该词汇的定制文章
4. **学习词汇**：文章中的目标词汇会被高亮显示
5. **保存分享**：使用复制或分享功能保存学习内容

### 系统要求

- Android 8.0 (API level 26) 或更高版本
- Java 11
- 网络连接

## 项目结构

```
app/src/main/java/com/wordcontextai/
├── data/           # 数据模型
├── network/        # 网络层
├── repository/     # 数据仓库
├── viewmodel/      # ViewModel层
├── adapter/        # RecyclerView适配器
└── MainActivity.kt # 主Activity
```

## 构建说明

1. 克隆项目到本地
2. 在Android Studio中打开项目
3. 确保已安装Java 11
4. 配置DeepSeek API密钥
5. 同步项目并运行
