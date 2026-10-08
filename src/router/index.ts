import { createRouter, createWebHistory } from "vue-router";

import Login from "../components/views/Login.vue";
import SignUp from "../components/views/SignUp.vue";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/",
      component: Login,
    },
    {
      path: "/signup",
      component: SignUp,
    },
  ],
});

export default router;
