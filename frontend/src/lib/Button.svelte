<script>

    let buttonProps = {
        class:[$$restProps.class]
    }
</script>
<button on:click
        on:mouseover
        on:mouseenter
        on:mouseleave
        {...buttonProps}>
    <slot/>
</button>

<style>
    button{
        background-color: #5f9361;
    }
</style>