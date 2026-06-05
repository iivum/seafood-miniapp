Component({
  properties: {
    product: { type: Object, value: null },
  },
  methods: {
    onTap() {
      if (!this.data.product) return;
      this.triggerEvent('tap', { product: this.data.product });
    },
    onAdd() {
      if (!this.data.product) return;
      this.triggerEvent('add', { product: this.data.product });
    },
  },
});
