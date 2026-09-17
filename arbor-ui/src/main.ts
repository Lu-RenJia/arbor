import { createApp } from 'vue'
import { createPinia } from 'pinia'
import naive from 'naive-ui'
import App from './App.vue'
import router from './router'
import { hasPermi } from './directives/hasPermi'
import { hasRole } from './directives/hasRole'
import './assets/global.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(naive)
app.directive('hasPermi', hasPermi)
app.directive('hasRole', hasRole)
app.mount('#app')
