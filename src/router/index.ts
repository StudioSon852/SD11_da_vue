import { createRouter, createWebHistory } from "vue-router";

import HomeView from "../views/HomeView.vue";
import Login from "../views/Login.vue";
import SignUp from "../views/SignUp.vue";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/",
      name: "home",
      component: HomeView,
    },
    {
      path: "/login",
      name: "login",
      component: Login,
    },
    {
      path: "/signup",
      name: "signup",
      component: SignUp,
    },
  ],
  scrollBehavior: () => ({ top: 0 }),
});

export default router;
