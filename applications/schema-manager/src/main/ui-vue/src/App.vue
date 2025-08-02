<template>
  <div id="app">
    <ChunkerConfigForm />
  </div>
</template>

<script>
import ChunkerConfigForm from './components/ChunkerConfigForm.vue'

export default {
  name: 'App',
  components: {
    ChunkerConfigForm
  }
}
</script>

<style>
* {
  box-sizing: border-box;
}

body {
  margin: 0;
  padding: 20px;
  background-color: #fafbfc;
}

#app {
  min-height: 100vh;
}
</style>