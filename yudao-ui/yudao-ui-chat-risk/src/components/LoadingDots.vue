<template>
  <div class="loading-dots">
    <span></span>
    <span></span>
    <span></span>
  </div>
</template>

<style scoped>
.loading-dots {
  display: inline-flex;
  gap: 4px;
  align-items: center;
}

.loading-dots span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background-color: currentColor;
  animation: bounce 1.4s infinite ease-in-out both;
}

.loading-dots span:nth-child(1) {
  animation-delay: -0.32s;
}

.loading-dots span:nth-child(2) {
  animation-delay: -0.16s;
}

@keyframes bounce {
  0%,
  80%,
  100% {
    transform: scale(0);
  }

  40% {
    transform: scale(1);
  }
}
</style>
