const CART_KEY = 'SEAFOOD_CART';

const getCart = () => {
  return wx.getStorageSync(CART_KEY) || [];
};

const addToCart = (product, quantity = 1) => {
  let cart = getCart();
  const index = cart.findIndex(item => item.id === product.id);
  if (index > -1) {
    cart[index].quantity += quantity;
  } else {
    cart.push({
      id: product.id,
      name: product.name,
      price: product.price,
      imageUrl: product.imageUrl,
      quantity: quantity
    });
  }
  wx.setStorageSync(CART_KEY, cart);
};

const removeFromCart = (productId) => {
  let cart = getCart();
  cart = cart.filter(item => item.id !== productId);
  wx.setStorageSync(CART_KEY, cart);
};

const updateQuantity = (productId, quantity) => {
  let cart = getCart();
  const index = cart.findIndex(item => item.id === productId);
  if (index > -1) {
    cart[index].quantity = quantity;
    if (cart[index].quantity <= 0) {
      cart.splice(index, 1);
    }
  }
  wx.setStorageSync(CART_KEY, cart);
};

const clearCart = () => {
  wx.removeStorageSync(CART_KEY);
};

module.exports = {
  getCart,
  addToCart,
  removeFromCart,
  updateQuantity,
  clearCart
};
