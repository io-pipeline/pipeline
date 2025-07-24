<template>
  <div id="app">
    <ParserConfigForm />
  </div>
</template>

<script>
import ParserConfigForm from './components/ParserConfigForm.vue'

export default {
  name: 'App',
  components: {
    ParserConfigForm
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