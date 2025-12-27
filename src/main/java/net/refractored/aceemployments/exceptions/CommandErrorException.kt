package net.refractored.aceemployments.exceptions

import net.kyori.adventure.text.Component

class CommandErrorException(
    val component: Component,
) : Exception(component.toString()) {
    constructor(message: String) : this(net.kyori.adventure.text.Component.text(message))
}
