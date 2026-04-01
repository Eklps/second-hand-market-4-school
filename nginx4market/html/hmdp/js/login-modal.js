Vue.component("login-modal", {
    template: `
    <div class="login-modal-mask" @click.self="$emit('close')">
        <div class="login-modal-container">
            <div class="login-modal-close" @click="$emit('close')"><i class="el-icon-close"></i></div>
            
            <div class="login-modal-title">登录后即可发现更多精彩</div>
            
            <div class="login-modal-tabs">
                <div class="login-tab-item" :class="{active: mode === 'code'}" @click="mode = 'code'">验证码登录</div>
                <div class="login-tab-item" :class="{active: mode === 'password'}" @click="mode = 'password'">密码登录</div>
            </div>

            <!-- 手机号输入框 -->
            <div class="login-input-wrap">
                <span class="login-input-prefix">+86 <i class="el-icon-caret-bottom" style="font-size:10px;margin-left:4px;"></i></span>
                <input type="tel" v-model="form.phone" placeholder="请输入手机号" class="login-modal-input" maxlength="11">
            </div>

            <!-- 验证码模式 -->
            <div v-if="mode === 'code'">
                <div class="login-input-wrap">
                    <input type="number" v-model="form.code" placeholder="请输入验证码" class="login-modal-input">
                    <button class="get-code-btn" :disabled="disabled" @click="sendCode">{{codeBtnMsg}}</button>
                </div>
            </div>

            <!-- 密码模式 -->
            <div v-else>
                <div class="login-input-wrap">
                    <input type="password" v-model="form.password" placeholder="请输入密码" class="login-modal-input">
                </div>
            </div>

            <!-- 底部协议 -->
            <div class="login-protocol">
                <input type="checkbox" v-model="agreed">
                <div>已阅读并同意 <a href="#">用户协议</a> 和 <a href="#">隐私政策</a>，未注册手机号优先验证后自动注册</div>
            </div>

            <!-- 登录按钮 -->
            <button class="login-submit-btn" :disabled="loading" @click="doLogin">
                <i v-if="loading" class="el-icon-loading"></i> {{loading ? '登录中...' : '登录'}}
            </button>
            
            <div class="footer-tip">第三方登录即将上线</div>
        </div>
    </div>
    `,
    data() {
        return {
            mode: 'code', // 'code' or 'password'
            form: {
                phone: '',
                code: '',
                password: ''
            },
            agreed: false,
            disabled: false,
            codeBtnMsg: "获取验证码",
            loading: false
        };
    },
    methods: {
        sendCode() {
            if (!this.form.phone || !/^1[3-9]\d{9}$/.test(this.form.phone)) {
                this.$root.$message.error("请输入正确的手机号");
                return;
            }
            // 发送验证码逻辑
            axios.post("/user/code?phone=" + this.form.phone)
                .then(() => {
                    this.$root.$message.success("验证码已发送至您的手机");
                    this.startCountdown();
                })
                .catch(err => this.$root.$message.error(err));
        },
        startCountdown() {
            this.disabled = true;
            let i = 60;
            this.codeBtnMsg = i + 's';
            let taskId = setInterval(() => {
                i--;
                this.codeBtnMsg = i + 's';
                if (i <= 0) {
                    this.disabled = false;
                    this.codeBtnMsg = "获取验证码";
                    clearInterval(taskId);
                }
            }, 1000);
        },
        doLogin() {
            if (!this.agreed) {
                this.$root.$message.warning("请先勾选并同意用户协议");
                return;
            }
            if (!this.form.phone) {
                this.$root.$message.error("请输入手机号");
                return;
            }
            
            this.loading = true;
            const url = this.mode === 'code' ? "/user/login" : "/user/login/pwd"; // 假设后端有密码登录路径
            const payload = this.mode === 'code' 
                ? { phone: this.form.phone, code: this.form.code }
                : { phone: this.form.phone, password: this.form.password };

            axios.post(url, payload)
                .then(({ data }) => {
                    if (data) {
                        sessionStorage.setItem("token", data);
                        this.$root.$message.success("登录成功！");
                        this.$emit('success');
                        // 登录成功后刷新页面或获取用户信息
                        location.reload(); 
                    }
                })
                .catch(err => this.$root.$message.error(err))
                .finally(() => this.loading = false);
        }
    }
});
