package dev.moonaticks.customGuiReworked.skript.expressions;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.ExpressionType;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import dev.moonaticks.customGuiReworked.api.animation.DesignAnimation;
import org.bukkit.event.Event;

/**
 * {@code cgui progress stage of %number% (out of|/) %number% (in|with) %number% [frames|stages]}
 *
 * <p>Индекс кадра (0-based) для стрелки прогресса: какой из кадров
 * показывать при текущем прогрессе (см. {@code DesignAnimation.stageForProgress}).
 *
 * <pre>{@code
 * set item slot 5 of player's inventory to item in list arrow[stage]
 * set stage to cgui progress stage of cook out of 200 in 4 frames
 * }</pre>
 */
@SuppressWarnings("deprecation")
public class ExprCguiStage extends SimpleExpression<Number> {

    private Expression<Number> progress;
    private Expression<Number> total;
    private Expression<Number> stages;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parser) {
        this.progress = (Expression<Number>) exprs[0];
        this.total = (Expression<Number>) exprs[1];
        this.stages = (Expression<Number>) exprs[2];
        return true;
    }

    @Override
    public Class<? extends Number> getReturnType() {
        return Number.class;
    }

    @Override
    public boolean isSingle() {
        return true;
    }

    @Override
    public String toString(Event event, boolean debug) {
        return "cgui progress stage of " + progress.toString(event, debug)
                + " out of " + total.toString(event, debug)
                + " in " + stages.toString(event, debug) + " frames";
    }

    @Override
    protected Number[] get(Event event) {
        Number p = progress.getSingle(event);
        Number t = total.getSingle(event);
        Number s = stages.getSingle(event);
        if (p == null || t == null || s == null) {
            return null;
        }
        int stage = DesignAnimation.stageForProgress(p.intValue(), t.intValue(), s.intValue());
        return new Number[]{stage};
    }
}
