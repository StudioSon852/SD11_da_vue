import { createRouter, createWebHistory } from "vue-router";

import HomeView from "../views/HomeView.vue";
import Login from "../views/Login.vue";
import SignUp from "../views/SignUp.vue";
import CartView from "../views/CartView.vue";
import CheckoutView from "../views/CheckoutView.vue";
import PaymentView from "../views/PaymentView.vue";

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
    {
      path: "/cart",
      name: "cart",
      component: CartView,
    },
    {
      path: "/checkout",
      name: "checkout",
      component: CheckoutView,
    },
    {
      path: "/payment",
      name: "payment",
      component: PaymentView,
    },
  ],
  scrollBehavior: () => ({ top: 0 }),
});

export default router;
