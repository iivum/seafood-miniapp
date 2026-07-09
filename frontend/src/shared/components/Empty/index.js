Component({
  options: {
    multipleSlots: true,
  },
  properties: {
    message: { type: String, value: '暂无数据' },
    retryable: { type: Boolean, value: false },
    icon: { type: String, value: 'search' },
    retryText: { type: String, value: '重试' },
  },
  methods: {
    onRetry() {
      this.triggerEvent('retry');
    },
  },
});
