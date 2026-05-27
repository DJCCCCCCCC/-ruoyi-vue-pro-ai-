import { defineStore } from 'pinia'
import { store } from '@/store'
import { cloneDeep } from 'lodash-es'
import { flatMultiLevelRoutes, generateRoute, Layout } from '@/utils/routerHelper'
import { CACHE_KEY, useCache } from '@/hooks/web/useCache'

const { wsCache } = useCache()

const payOnlyRouters: AppRouteRecordRaw[] = [
  {
    path: '/pay',
    component: Layout,
    name: 'PayManage',
    redirect: '/pay/risk/assess',
    meta: {
      title: '支付管理',
      icon: 'ep:credit-card',
      alwaysShow: true
    },
    children: [
      {
        path: 'risk/assess',
        component: () => import('@/views/pay/risk/assess/index.vue'),
        name: 'PayRiskAssessMenu',
        meta: {
          title: '支付风险评估',
          icon: 'ep:warning',
          noCache: false,
          affix: true,
          activeMenu: '/pay/risk/assess'
        }
      },
      {
        path: 'risk/term',
        component: () => import('@/views/pay/risk/term/index.vue'),
        name: 'PayRiskTermMenu',
        meta: {
          title: '风险词库',
          icon: 'ep:collection',
          noCache: false,
          activeMenu: '/pay/risk/term'
        }
      },
      {
        path: 'risk/chat',
        component: () => import('@/views/pay/risk/chat/index.vue'),
        name: 'PayRiskChatMenu',
        meta: {
          title: '支付风险交互',
          icon: 'ep:chat-dot-round',
          noCache: false,
          activeMenu: '/pay/risk/chat'
        }
      }
    ]
  }
]

export interface PermissionState {
  routers: AppRouteRecordRaw[]
  addRouters: AppRouteRecordRaw[]
  menuTabRouters: AppRouteRecordRaw[]
}

export const usePermissionStore = defineStore('permission', {
  state: (): PermissionState => ({
    routers: [],
    addRouters: [],
    menuTabRouters: []
  }),
  getters: {
    getRouters(): AppRouteRecordRaw[] {
      return this.routers
    },
    getAddRouters(): AppRouteRecordRaw[] {
      return flatMultiLevelRoutes(cloneDeep(this.addRouters))
    },
    getMenuTabRouters(): AppRouteRecordRaw[] {
      return this.menuTabRouters
    }
  },
  actions: {
    async generateRoutes(): Promise<unknown> {
      return new Promise<void>(async (resolve) => {
        // 获得菜单列表，它在登录的时候，setUserInfoAction 方法中已经进行获取
        let res: AppCustomRouteRecordRaw[] = []
        const roleRouters = wsCache.get(CACHE_KEY.ROLE_ROUTERS)
        if (roleRouters) {
          res = roleRouters as AppCustomRouteRecordRaw[]
        }
        const routerMap: AppRouteRecordRaw[] = generateRoute(res)
        // 动态路由，404一定要放到最后面
        // preschooler：vue-router@4以后已支持静态404路由，此处可不再追加
        this.addRouters = payOnlyRouters.concat(routerMap, [
          {
            path: '/:path(.*)*',
            // redirect: '/404',
            component: () => import('@/views/Error/404.vue'),
            name: '404Page',
            meta: {
              hidden: true,
              breadcrumb: false
            }
          }
        ])
        // 渲染菜单的所有路由：左侧菜单只显示支付管理下的 3 个功能
        this.routers = cloneDeep(payOnlyRouters)
        resolve()
      })
    },
    setMenuTabRouters(routers: AppRouteRecordRaw[]): void {
      this.menuTabRouters = routers
    }
  },
  persist: false
})

export const usePermissionStoreWithOut = () => {
  return usePermissionStore(store)
}
