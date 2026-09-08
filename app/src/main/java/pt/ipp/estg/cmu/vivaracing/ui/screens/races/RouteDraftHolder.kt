package pt.ipp.estg.cmu.vivaracing.ui.screens.races

import pt.ipp.estg.cmu.vivaracing.data.model.GeoPoint
import pt.ipp.estg.cmu.vivaracing.data.model.RouteSource

/**
 * Transporte temporário do percurso entre o ecrã de gravação e o formulário de
 * criação de prova.
 *
 * O componente Navigation apenas transporta argumentos primitivos nas rotas,
 * pelo que um percurso com centenas de coordenadas não pode ser passado por
 * essa via. Como o formulário permanece na pilha de navegação enquanto o
 * utilizador grava o trajeto, basta um objeto partilhado de curta duração,
 * que é limpo assim que o percurso é consumido.
 */
object RouteDraftHolder {

    var points: List<GeoPoint> = emptyList()
        private set

    var distanceMeters: Double = 0.0
        private set

    var source: RouteSource = RouteSource.MANUAL
        private set

    fun store(points: List<GeoPoint>, distanceMeters: Double, source: RouteSource) {
        this.points = points
        this.distanceMeters = distanceMeters
        this.source = source
    }

    fun hasDraft(): Boolean = points.isNotEmpty()

    fun clear() {
        points = emptyList()
        distanceMeters = 0.0
        source = RouteSource.MANUAL
    }
}
