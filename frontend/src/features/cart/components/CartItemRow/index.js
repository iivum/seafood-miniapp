Component({
  properties: {
    item: { type: Object, value: null },
    readonly: { type: Boolean, value: false },
  },
  methods: {
    onToggle() {
      if (this.data.readonly) return;
      this.triggerEvent('toggle', { productId: this.data.item.productId });
    },
    onMinus() {
      if (this.data.readonly) return;
      this.triggerEvent('minus', { productId: this.data.item.productId });
    },
    onPlus() {
      if (this.data.readonly) return;
      this.triggerEvent('plus', { productId: this.data.item.productId });
    },
    onRemove() {
      if (this.data.readonly) return;
      this.triggerEvent('remove', { productId: this.data.item.productId });
    },
  },
});
