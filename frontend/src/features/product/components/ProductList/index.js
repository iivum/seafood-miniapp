Component({
  properties: {
    products: { type: Array, value: [] },
    loading: { type: Boolean, value: false },
    error: { type: String, value: '' },
  },
  methods: {
    onProductTap(e) {
      this.triggerEvent('producttap', e.detail);
    },
    onAddToCart(e) {
      this.triggerEvent('addtocart', e.detail);
    },
    onRetry() {
      this.triggerEvent('retry');
    },
  },
});
