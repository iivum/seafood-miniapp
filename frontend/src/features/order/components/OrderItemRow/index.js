Component({
  properties: {
    order: { type: Object, value: null },
  },
  methods: {
    onTap() {
      this.triggerEvent('tap', { order: this.data.order });
    },
    onCancel() {
      this.triggerEvent('cancel', { order: this.data.order });
    },
  },
});
