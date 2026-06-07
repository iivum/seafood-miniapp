Component({
  options: {
    multipleSlots: true,
  },
  properties: {
    message: { type: String, value: '暂无数据' },
    retryable: { type: Boolean, value: false },
  },
  methods: {
    onRetry() {
      this.triggerEvent('retry');
    },
  },
});
